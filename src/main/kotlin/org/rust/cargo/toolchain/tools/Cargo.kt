/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.isBuildToolWindowEnabled
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoPackage
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoProject
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoTargets
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.*
import org.rust.cargo.toolchain.RsToolchainBase.Companion.RUSTC_BOOTSTRAP
import org.rust.cargo.toolchain.RsToolchainBase.Companion.RUSTC_WRAPPER
import org.rust.cargo.toolchain.impl.BuildMessages
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.impl.CargoMetadata.replacePaths
import org.rust.cargo.toolchain.impl.CompilerMessage
import org.rust.cargo.toolchain.tools.ProjectDescriptionStatus.BUILD_SCRIPT_EVALUATION_ERROR
import org.rust.cargo.toolchain.tools.ProjectDescriptionStatus.OK
import org.rust.cargo.toolchain.tools.Rustup.Companion.checkNeedInstallClippy
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.cargo.util.parseSemVer
import org.rust.ide.actions.InstallBinaryCrateAction
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.notifications.showBalloon
import org.rust.lang.RsConstants.LIB_RS_FILE
import org.rust.lang.RsConstants.MAIN_RS_FILE
import org.rust.openapiext.*
import org.rust.openapiext.JsonUtils.tryParseJsonObject
import org.rust.stdext.buildList
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun RsToolchainBase.cargo(): Cargo = Cargo(this)

// BACKCOMPAT: 2021.1. Added not to break binary compatibility with EduTools plugin
@Suppress("DEPRECATION")
fun RsToolchain.cargo(): Cargo = (this as RsToolchainBase).cargo()

fun RsToolchainBase.cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
    val hasXargoToml = cargoProjectDirectory?.resolve(CargoConstants.XARGO_MANIFEST_FILE)
        ?.let { Files.isRegularFile(it) } == true
    val useWrapper = hasXargoToml && hasExecutable(Cargo.WRAPPER_NAME)
    return Cargo(this, useWrapper)
}

/**
 * A main gateway for executing cargo commands.
 *
 * This class is not aware of SDKs or projects, so you'll need to provide
 * paths yourself.
 *
 * It is impossible to guarantee that paths to the project or executables are valid,
 * because the user can always just `rm ~/.cargo/bin -rf`.
 */
class Cargo(toolchain: RsToolchainBase, useWrapper: Boolean = false)
    : RustupComponent(if (useWrapper) WRAPPER_NAME else NAME, toolchain) {

    data class BinaryCrate(val name: String, val version: SemVer? = null) {
        companion object {
            fun from(line: String): BinaryCrate {
                val name = line.substringBefore(' ')
                val rawVersion = line.substringAfter(' ').removePrefix("v").removeSuffix(":")
                return BinaryCrate(name, SemVer.parseFromText(rawVersion))
            }
        }
    }

    private fun listInstalledBinaryCrates(): List<BinaryCrate> =
        createBaseCommandLine("install", "--list")
            .execute(toolchain.executionTimeoutInMilliseconds)
            ?.stdoutLines
            ?.filterNot { it.startsWith(" ") }
            ?.map { BinaryCrate.from(it) }
            .orEmpty()

    fun installBinaryCrate(project: Project, crateName: String) {
        val cargoProject = project.cargoProjects.allProjects.firstOrNull() ?: return
        val commandLine = CargoCommandLine.forProject(cargoProject, "install", listOf("--force", crateName))
        commandLine.run(cargoProject, "Install $crateName", saveConfiguration = false)
    }

    fun checkSupportForBuildCheckAllTargets(): Boolean {
        val lines = createBaseCommandLine("help", "check")
            .execute(toolchain.executionTimeoutInMilliseconds)
            ?.stdoutLines
            ?: return false
        return lines.any { it.contains(" --all-targets ") }
    }

    /**
     * Fetch all dependencies and calculate project information.
     *
     * This is a potentially long running operation which can
     * legitimately fail due to network errors or inability
     * to resolve dependencies. Hence it is mandatory to
     * pass an [owner] to correctly kill the process if it
     * runs for too long.
     */
    @Throws(ExecutionException::class)
    fun fullProjectDescription(
        owner: Project,
        projectDirectory: Path,
        listenerProvider: (CargoCallType) -> ProcessListener? = { null }
    ): ProjectDescription {
        val rawData = fetchMetadata(owner, projectDirectory, listenerProvider(CargoCallType.METADATA))

        val buildScriptsInfo = if (isFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS)) {
            fetchBuildScriptsInfo(owner, projectDirectory, listenerProvider(CargoCallType.BUILD_SCRIPT_CHECK))
        } else {
            BuildMessages.DEFAULT
        }

        val (rawDataAdjusted, buildScriptsInfoAdjusted) =
            replacePathsSymlinkIfNeeded(rawData, buildScriptsInfo, projectDirectory)
        val workspaceData = CargoMetadata.clean(rawDataAdjusted, buildScriptsInfoAdjusted)
        val status = if (buildScriptsInfo.isSuccessful) OK else BUILD_SCRIPT_EVALUATION_ERROR
        return ProjectDescription(workspaceData, status)
    }

    @Throws(ExecutionException::class)
    fun fetchMetadata(
        owner: Project,
        projectDirectory: Path,
        listener: ProcessListener? = null
    ): CargoMetadata.Project {
        val additionalArgs = mutableListOf("--verbose", "--format-version", "1", "--all-features")
        val json = CargoCommandLine("metadata", projectDirectory, additionalArgs)
            .execute(owner, listener = listener)
            .stdout
            .dropWhile { it != '{' }
        try {
            return Gson().fromJson(json, CargoMetadata.Project::class.java)
                .convertPaths(toolchain::toLocalPath)
        } catch (e: JsonSyntaxException) {
            throw ExecutionException(e)
        }
    }

    @Throws(ExecutionException::class)
    fun vendorDependencies(
        owner: Project,
        projectDirectory: Path,
        dstPath: Path,
        listener: ProcessListener? = null
    ) {
        val commandLine = CargoCommandLine("vendor", projectDirectory, listOf(dstPath.toString()))
        commandLine.execute(owner, listener = listener)
    }

    /**
     * Execute `cargo rustc --print cfg` and parse output as [CfgOptions].
     * Available since Rust 1.52
     */
    @Throws(ExecutionException::class)
    fun getCfgOption(
        owner: Project,
        projectDirectory: Path?
    ): CfgOptions {
        val output = createBaseCommandLine(
            "rustc", "-Z", "unstable-options", "--print", "cfg",
            workingDirectory = projectDirectory,
            environment = mapOf(RUSTC_BOOTSTRAP to "1")
        ).execute(owner, ignoreExitCode = false)
        return CfgOptions.parse(output.stdoutLines)
    }

    private fun fetchBuildScriptsInfo(
        owner: Project,
        projectDirectory: Path,
        listener: ProcessListener?
    ): BuildMessages {
        // `--tests` is needed here to compile dev dependencies during build script evaluation.
        // `--all-targets` also can help to build dev dependencies,
        // but it may force unnecessary compilation of examples, benches and other targets
        val additionalArgs = listOf("--message-format", "json", "--workspace", "--tests")
        val nativeHelper = RsPathManager.nativeHelper(toolchain is RsWslToolchain)
        val envs = if (nativeHelper != null && Registry.`is`("org.rust.cargo.evaluate.build.scripts.wrapper")) {
            EnvironmentVariablesData.create(mapOf(RUSTC_WRAPPER to nativeHelper.toString()), true)
        } else {
            EnvironmentVariablesData.DEFAULT
        }

        val commandLine = CargoCommandLine("check", projectDirectory, additionalArgs, environmentVariables = envs)

        val processOutput = try {
            commandLine.execute(owner, ignoreExitCode = true, listener = listener)
        } catch (e: ExecutionException) {
            LOG.warn(e)
            return BuildMessages.FAILED
        }

        val messages = mutableMapOf<PackageId, MutableList<CompilerMessage>>()

        for (line in processOutput.stdoutLines) {
            val jsonObject = tryParseJsonObject(line) ?: continue
            CompilerMessage.fromJson(jsonObject)
                ?.convertPaths(toolchain::toLocalPath)
                ?.let { messages.getOrPut(it.package_id) { mutableListOf() } += it }
        }
        return BuildMessages(messages, isSuccessful = processOutput.exitCode == 0)
    }

    /**
     * If project directory is 'source' which is a symlink to 'target' directory,
     * then `cargo metadata` command inside 'source' directory produces output with 'target' paths.
     * This function replaces paths to 'source'.
     * 'source' paths are better because files inside 'source' are indexed, and inside 'target' are not.
     */
    private fun replacePathsSymlinkIfNeeded(
        project: CargoMetadata.Project,
        buildMessages: BuildMessages?,
        projectDirectoryRel: Path
    ): Pair<CargoMetadata.Project, BuildMessages?> {
        val projectDirectory = projectDirectoryRel.toAbsolutePath()
        val workspaceRoot = project.workspace_root

        if (projectDirectory.toString() == workspaceRoot) {
            return Pair(project, buildMessages)
        }

        val workspaceRootPath = Paths.get(workspaceRoot)

        // If the selected projectDirectory doesn't resolve directly to the directory that Cargo spat out at us,
        // then there's something a bit special with the cargo workspace, and we don't want to assume anything.
        if (!Files.isSameFile(projectDirectory, workspaceRootPath)) {
            return Pair(project, buildMessages)
        }

        // Otherwise, it's just a normal symlink.
        val normalisedWorkspace = projectDirectory.normalize().toString()
        val replacer: (String) -> String = replacer@{
            if (!it.startsWith(workspaceRoot)) return@replacer it
            normalisedWorkspace + it.removePrefix(workspaceRoot)
        }
        return Pair(project.replacePaths(replacer), buildMessages?.replacePaths(replacer))
    }

    @Throws(ExecutionException::class)
    fun init(
        project: Project,
        owner: Disposable,
        directory: VirtualFile,
        name: String,
        createBinary: Boolean,
        vcs: String? = null
    ): GeneratedFilesHolder {
        val path = directory.pathAsPath
        val crateType = if (createBinary) "--bin" else "--lib"

        val args = mutableListOf(crateType, "--name", name)

        vcs?.let {
            args.addAll(listOf("--vcs", vcs))
        }

        args.add(path.toString())

        CargoCommandLine("init", path, args).execute(project, owner)
        fullyRefreshDirectory(directory)

        val manifest = checkNotNull(directory.findChild(CargoConstants.MANIFEST_FILE)) { "Can't find the manifest file" }
        val fileName = if (createBinary) MAIN_RS_FILE else LIB_RS_FILE
        val sourceFiles = listOfNotNull(directory.findFileByRelativePath("src/$fileName"))
        return GeneratedFilesHolder(manifest, sourceFiles)
    }

    @Throws(ExecutionException::class)
    fun generate(
        project: Project,
        owner: Disposable,
        directory: VirtualFile,
        name: String,
        templateUrl: String
    ): GeneratedFilesHolder {
        val path = directory.pathAsPath
        val args = mutableListOf(
            "--name", name,
            "--git", templateUrl,
            "--init", // generate in current directory
            "--force" // enforce cargo-generate not to do underscores to hyphens name conversion
        )

        CargoCommandLine("generate", path, args).execute(project, owner)
        fullyRefreshDirectory(directory)

        val manifest = checkNotNull(directory.findChild(CargoConstants.MANIFEST_FILE)) { "Can't find the manifest file" }
        val sourceFiles = listOf("main", "lib").mapNotNull { directory.findFileByRelativePath("src/${it}.rs") }
        return GeneratedFilesHolder(manifest, sourceFiles)
    }

    @Throws(ExecutionException::class)
    fun checkProject(
        project: Project,
        owner: Disposable,
        args: CargoCheckArgs
    ): ProcessOutput {
        val useClippy = args.linter == ExternalLinter.CLIPPY
            && !checkNeedInstallClippy(project, args.cargoProjectDirectory)
        val checkCommand = if (useClippy) "clippy" else "check"

        val commandLine = when (args) {
            is CargoCheckArgs.SpecificTarget -> {
                val arguments = buildList<String> {
                    add("--message-format=json")
                    add("--no-default-features")
                    val enabledFeatures = args.target.pkg.featureState.filterValues { it.isEnabled }.keys.toList()
                    if (enabledFeatures.isNotEmpty()) {
                        add("--features")
                        add(enabledFeatures.joinToString(separator = " "))
                    }
                    if (args.target.kind !is CargoWorkspace.TargetKind.Test) {
                        // Check `#[test]`/`#[cfg(test)]`
                        // TODO try using `--profile test`, see https://github.com/intellij-rust/intellij-rust/issues/6277
                        add("--tests")
                    }
                    addAll(ParametersListUtil.parse(args.extraArguments))
                }
                CargoCommandLine.forTarget(args.target, checkCommand, arguments, usePackageOption = false)
            }
            is CargoCheckArgs.FullWorkspace -> {
                val arguments = buildList<String> {
                    add("--message-format=json")
                    add("--all")
                    if (args.allTargets && checkSupportForBuildCheckAllTargets()) {
                        add("--all-targets")
                    }
                    addAll(ParametersListUtil.parse(args.extraArguments))
                }
                CargoCommandLine(checkCommand, args.cargoProjectDirectory, arguments)
            }
        }

        return commandLine.execute(project, owner, ignoreExitCode = true)
    }

    fun toColoredCommandLine(project: Project, commandLine: CargoCommandLine): GeneralCommandLine =
        toGeneralCommandLine(project, commandLine, colors = true)

    fun toGeneralCommandLine(project: Project, commandLine: CargoCommandLine): GeneralCommandLine =
        toGeneralCommandLine(project, commandLine, colors = false)

    private fun toGeneralCommandLine(project: Project, commandLine: CargoCommandLine, colors: Boolean): GeneralCommandLine =
        with(commandLine.patchArgs(project, colors)) {
            val parameters = buildList<String> {
                if (channel != RustChannel.DEFAULT) {
                    add("+$channel")
                }
                if (project.rustSettings.useOffline) {
                    val cargoProject = findCargoProject(project, additionalArguments, workingDirectory)
                    val rustcVersion = cargoProject?.rustcInfo?.version?.semver
                    if (rustcVersion != null) {
                        add("--offline")
                    }
                }
                add(command)
                addAll(additionalArguments)
            }
            val rustcExecutable = toolchain.rustc().executable.toString()
            toolchain.createGeneralCommandLine(
                executable,
                workingDirectory,
                redirectInputFrom,
                backtraceMode,
                environmentVariables,
                parameters,
                emulateTerminal,
                // TODO: always pass `withSudo` when `com.intellij.execution.process.ElevationService` supports error stream redirection
                // https://github.com/intellij-rust/intellij-rust/issues/7320
                if (project.isBuildToolWindowEnabled) withSudo else false,
                http = http
            ).withEnvironment("RUSTC", rustcExecutable)
        }

    @Throws(ExecutionException::class)
    private fun CargoCommandLine.execute(
        project: Project,
        owner: Disposable = project,
        ignoreExitCode: Boolean = false,
        stdIn: ByteArray? = null,
        listener: ProcessListener? = null
    ): ProcessOutput = toGeneralCommandLine(project, this).execute(owner, ignoreExitCode, stdIn, listener = listener)

    fun installCargoGenerate(owner: Disposable, listener: ProcessListener) {
        createBaseCommandLine("install", "cargo-generate").execute(owner, listener = listener)
    }

    fun checkNeedInstallCargoGenerate(): Boolean {
        val crateName = "cargo-generate"
        val minVersion = "0.9.0".parseSemVer()
        return checkBinaryCrateIsNotInstalled(crateName, minVersion)
    }

    private fun checkBinaryCrateIsNotInstalled(crateName: String, minVersion: SemVer?): Boolean {
        val installed = listInstalledBinaryCrates().any { (name, version) ->
            name == crateName && (minVersion == null || version != null && version >= minVersion)
        }
        return !installed
    }

    private var _http: HttpConfigurable? = null
    private val http: HttpConfigurable
        get() = _http ?: HttpConfigurable.getInstance()

    @TestOnly
    fun setHttp(http: HttpConfigurable) {
        _http = http
    }

    companion object {
        private val LOG: Logger = logger<Cargo>()

        @JvmStatic
        val TEST_NOCAPTURE_ENABLED_KEY: RegistryValue = Registry.get("org.rust.cargo.test.nocapture")

        const val NAME: String = "cargo"
        const val WRAPPER_NAME: String = "xargo"

        private val FEATURES_ACCEPTING_COMMANDS: List<String> = listOf(
            "bench", "build", "check", "doc", "fix", "run", "rustc", "rustdoc", "test", "metadata", "tree", "install", "package", "publish"
        )

        private val COLOR_ACCEPTING_COMMANDS: List<String> = listOf(
            "bench", "build", "check", "clean", "clippy", "doc", "install", "publish", "run", "rustc", "test", "update"
        )

        data class GeneratedFilesHolder(val manifest: VirtualFile, val sourceFiles: List<VirtualFile>)

        fun getCargoCommonPatch(project: Project): CargoPatch = { it.patchArgs(project, true) }

        fun CargoCommandLine.patchArgs(project: Project, colors: Boolean): CargoCommandLine {
            val (pre, post) = splitOnDoubleDash()
                .let { (pre, post) -> pre.toMutableList() to post.toMutableList() }

            if (command == "test") {
                if (allFeatures && !pre.contains("--all-features")) {
                    pre.add("--all-features")
                }
                if (TEST_NOCAPTURE_ENABLED_KEY.asBoolean() && !post.contains("--nocapture")) {
                    post.add(0, "--nocapture")
                }
            }

            if (requiredFeatures && command in FEATURES_ACCEPTING_COMMANDS) {
                run {
                    val cargoProject = findCargoProject(project, additionalArguments, workingDirectory) ?: return@run
                    val cargoPackage = findCargoPackage(cargoProject, additionalArguments, workingDirectory) ?: return@run
                    if (workingDirectory != cargoPackage.rootDirectory) {
                        val manifestIdx = pre.indexOf("--manifest-path")
                        val packageIdx = pre.indexOf("--package")
                        if (manifestIdx == -1 && packageIdx != -1) {
                            pre.removeAt(packageIdx) // remove `--package`
                            pre.removeAt(packageIdx) // remove package name
                            pre.add("--manifest-path")
                            val manifest = cargoPackage.rootDirectory.resolve(CargoConstants.MANIFEST_FILE)
                            pre.add(manifest.toAbsolutePath().toString())
                        }
                    }
                    val cargoTargets = findCargoTargets(cargoPackage, additionalArguments)
                    val features = cargoTargets.flatMap { it.requiredFeatures }.distinct().joinToString(",")
                    if (features.isNotEmpty()) pre.add("--features=$features")
                }
            }

            // Force colors
            val forceColors = colors &&
                command in COLOR_ACCEPTING_COMMANDS &&
                additionalArguments.none { it.startsWith("--color") }
            if (forceColors) pre.add(0, "--color=always")

            return copy(additionalArguments = if (post.isEmpty()) pre else pre + "--" + post)
        }

        fun checkNeedInstallGrcov(project: Project): Boolean {
            val crateName = "grcov"
            val minVersion = "0.7.0".parseSemVer()
            return checkNeedInstallBinaryCrate(
                project,
                crateName,
                NotificationType.ERROR,
                "Need at least $crateName $minVersion",
                minVersion
            )
        }

        fun checkNeedInstallCargoExpand(project: Project): Boolean {
            val crateName = "cargo-expand"
            val minVersion = "1.0.0".parseSemVer()
            return checkNeedInstallBinaryCrate(
                project,
                crateName,
                NotificationType.ERROR,
                "Need at least $crateName $minVersion",
                minVersion
            )
        }

        fun checkNeedInstallEvcxr(project: Project): Boolean {
            val crateName = "evcxr_repl"
            val minVersion = "0.10.0".parseSemVer()
            return checkNeedInstallBinaryCrate(
                project,
                crateName,
                NotificationType.ERROR,
                "Need at least $crateName $minVersion",
                minVersion
            )
        }

        fun checkNeedInstallWasmPack(project: Project): Boolean {
            val crateName = "wasm-pack"
            val minVersion = "0.9.1".parseSemVer()
            return checkNeedInstallBinaryCrate(
                project,
                crateName,
                NotificationType.ERROR,
                "Need at least $crateName $minVersion",
                minVersion
            )
        }

        private fun checkNeedInstallBinaryCrate(
            project: Project,
            crateName: String,
            notificationType: NotificationType,
            message: String? = null,
            minVersion: SemVer? = null
        ): Boolean {
            val cargo = project.toolchain?.cargo() ?: return false
            val isNotInstalled = { cargo.checkBinaryCrateIsNotInstalled(crateName, minVersion) }
            val needInstall = if (isDispatchThread) {
                project.computeWithCancelableProgress("Checking if $crateName is installed...", isNotInstalled)
            } else {
                isNotInstalled()
            }

            if (needInstall) {
                project.showBalloon(
                    "<code>$crateName</code> is not installed",
                    message ?: "",
                    notificationType,
                    InstallBinaryCrateAction(crateName)
                )
            }

            return needInstall
        }
    }
}

// Note: the class is used as a cache key, so it must be immutable and must have a correct equals/hashCode
sealed class CargoCheckArgs {
    abstract val linter: ExternalLinter
    abstract val cargoProjectDirectory: Path
    abstract val extraArguments: String

    data class SpecificTarget(
        override val linter: ExternalLinter,
        override val cargoProjectDirectory: Path,
        val target: CargoWorkspace.Target,
        override val extraArguments: String
    ) : CargoCheckArgs()

    data class FullWorkspace(
        override val linter: ExternalLinter,
        override val cargoProjectDirectory: Path,
        val allTargets: Boolean,
        override val extraArguments: String
    ) : CargoCheckArgs()

    companion object {
        fun forTarget(project: Project, target: CargoWorkspace.Target): CargoCheckArgs {
            val settings = project.rustSettings
            return SpecificTarget(
                settings.externalLinter,
                target.pkg.workspace.contentRoot,
                target,
                settings.externalLinterArguments
            )
        }

        fun forCargoProject(cargoProject: CargoProject): CargoCheckArgs {
            val settings = cargoProject.project.rustSettings
            return FullWorkspace(
                settings.externalLinter,
                cargoProject.workingDirectory,
                settings.compileAllTargets,
                settings.externalLinterArguments
            )
        }
    }
}

enum class CargoCallType {
    METADATA,
    BUILD_SCRIPT_CHECK
}

data class ProjectDescription(
    val workspaceData: CargoWorkspaceData,
    val status: ProjectDescriptionStatus
)

enum class ProjectDescriptionStatus {
    BUILD_SCRIPT_EVALUATION_ERROR,
    OK
}
