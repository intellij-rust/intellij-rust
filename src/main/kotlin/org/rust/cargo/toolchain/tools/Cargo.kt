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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.Testmark
import com.intellij.openapiext.isDispatchThread
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoPackage
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoProject
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoTargets
import org.rust.cargo.runconfig.command.workingDirectory
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.ExternalLinter
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.RsToolchain.Companion.RUSTC_BOOTSTRAP
import org.rust.cargo.toolchain.RsToolchain.Companion.RUSTC_WRAPPER
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.impl.*
import org.rust.cargo.toolchain.impl.CargoMetadata.replacePaths
import org.rust.cargo.toolchain.tools.Rustup.Companion.checkNeedInstallClippy
import org.rust.ide.actions.InstallBinaryCrateAction
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.*
import org.rust.openapiext.JsonUtils.tryParseJsonObject
import org.rust.stdext.buildList
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun RsToolchain.cargo(): Cargo = Cargo(this)

fun RsToolchain.cargoOrWrapper(cargoProjectDirectory: Path?): Cargo {
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
open class Cargo(toolchain: RsToolchain, useWrapper: Boolean = false)
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

    fun listInstalledBinaryCrates(): List<BinaryCrate> =
        createBaseCommandLine("install", "--list")
            .execute()
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
        val lines = createBaseCommandLine("help", "check").execute()?.stdoutLines ?: return false
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
        listener: ProcessListener? = null
    ): CargoWorkspaceData {
        val rawData = fetchMetadata(owner, projectDirectory, listener)
        val buildScriptsInfo = fetchBuildScriptsInfo(owner, projectDirectory, listener)
        val buildPlan = if (buildScriptsInfo?.containsOutDirInfo != true) {
            fetchBuildPlan(owner, projectDirectory, listener)
        } else {
            null
        }

        val (rawDataAdjusted, buildScriptsInfoAdjusted) =
            replacePathsSymlinkIfNeeded(rawData, buildScriptsInfo, projectDirectory)
        return CargoMetadata.clean(rawDataAdjusted, buildScriptsInfoAdjusted, buildPlan)
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
        } catch (e: JsonSyntaxException) {
            throw ExecutionException(e)
        }
    }

    @Throws(ExecutionException::class)
    fun vendorDependencies(
        owner: Project,
        projectDirectory: Path,
        dstPath: Path
    ) {
        val commandLine = CargoCommandLine("vendor", projectDirectory, listOf(dstPath.toString()))
        commandLine.execute(owner)
    }

    private fun fetchBuildScriptsInfo(
        owner: Project,
        projectDirectory: Path,
        listener: ProcessListener?
    ): BuildMessages? {
        if (!isFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS)) return null
        val additionalArgs = listOf("--message-format", "json")
        val nativeHelper = RsPathManager.nativeHelper()
        val envs = if (nativeHelper != null && Registry.`is`("org.rust.cargo.evaluate.build.scripts.wrapper")) {
            EnvironmentVariablesData.create(mapOf(RUSTC_WRAPPER to nativeHelper.toString()), true)
        } else {
            EnvironmentVariablesData.DEFAULT
        }

        val commandLine = CargoCommandLine("check", projectDirectory, additionalArgs, environmentVariables = envs)

        val processOutput = try {
            commandLine.execute(owner, listener = listener)
        } catch (e: ExecutionException) {
            LOG.warn(e)
            return null
        }

        val messages = mutableMapOf<PackageId, MutableList<CompilerMessage>>()

        for (line in processOutput.stdoutLines) {
            val jsonObject = tryParseJsonObject(line) ?: continue
            CompilerMessage.fromJson(jsonObject)?.let { messages.getOrPut(it.package_id) { mutableListOf() } += it }
        }
        return BuildMessages(messages)
    }

    private fun fetchBuildPlan(
        owner: Project,
        projectDirectory: Path,
        listener: ProcessListener?
    ): CargoBuildPlan? {
        if (!isFeatureEnabled(RsExperiments.FETCH_OUT_DIR)) return null
        Testmarks.fetchBuildPlan.hit()
        val additionalArgs = mutableListOf("-Z", "unstable-options", "--all-targets", "--build-plan")
        // Hack to make cargo think that unstable options are available because we need unstable `--build-plan` option here
        val envs = EnvironmentVariablesData.create(mapOf(
            RUSTC_BOOTSTRAP to "1"
        ), true)
        return try {
            val json = CargoCommandLine("build", projectDirectory, additionalArgs, environmentVariables = envs)
                .execute(owner, listener = listener)
                .stdout
            Gson().fromJson(json, CargoBuildPlan::class.java)
        } catch (e: Exception) {
            LOG.warn("Failed to fetch build-plan", e)
            null
        }
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
        projectDirectory: Path
    ): Pair<CargoMetadata.Project, BuildMessages?> {
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
        val fileName = if (createBinary) "main.rs" else "lib.rs"
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
    ): GeneratedFilesHolder? {
        val path = directory.pathAsPath
        val args = mutableListOf("--name", name, "--git", templateUrl)
        args.add("--force") // enforce cargo-generate not to do underscores to hyphens name conversion

        // TODO: Rewrite this for the future versions of cargo-generate when init subcommand will be available
        // See https://github.com/ashleygwilliams/cargo-generate/issues/193

        // Generate a cargo-generate project inside a subdir
        CargoCommandLine("generate", path, args).execute(project, owner)

        // Move all the generated files to the project root and delete the subdir itself
        val generatedDir = try {
            File(path.toString(), name)
        } catch (e: NullPointerException) {
            LOG.warn("Failed to generate project using cargo-generate")
            return null
        }
        val generatedFiles = generatedDir.walk().drop(1) // drop the `generatedDir` itself
        for (generatedFile in generatedFiles) {
            val newFile = File(path.toString(), generatedFile.name)
            Files.move(generatedFile.toPath(), newFile.toPath())
        }
        generatedDir.delete()

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
                    when {
                        rustcVersion == null -> Unit
                        rustcVersion < RUST_1_36 -> add("-Zoffline")
                        else -> add("--offline")
                    }
                }
                add(command)
                addAll(additionalArguments)
            }
            val rustcExecutable = toolchain.rustc().executable.toString()
            createGeneralCommandLine(
                executable,
                workingDirectory,
                redirectInputFrom,
                backtraceMode,
                environmentVariables,
                parameters,
                emulateTerminal,
                http
            ).withEnvironment("RUSTC", rustcExecutable)
        }

    @Throws(ExecutionException::class)
    private fun CargoCommandLine.execute(
        project: Project,
        owner: Disposable = project,
        ignoreExitCode: Boolean = false,
        stdIn: ByteArray? = null,
        listener: ProcessListener? = null
    ): ProcessOutput = toGeneralCommandLine(project, this).execute(owner, ignoreExitCode, stdIn, listener)

    fun installCargoGenerate(owner: Disposable, listener: ProcessListener) {
        createBaseCommandLine("install", "cargo-generate").execute(owner, listener = listener)
    }

    fun checkNeedInstallCargoGenerate(): Boolean {
        val crateName = "cargo-generate"
        val minVersion = SemVer("v0.5.0", 0, 5, 0)
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
        private val LOG: Logger = Logger.getInstance(Cargo::class.java)

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

            if (command == "test" && allFeatures && !pre.contains("--all-features")) {
                pre.add("--all-features")
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
            val minVersion = SemVer("v0.4.3", 0, 4, 3)
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
            val minVersion = SemVer("v0.4.9", 0, 4, 9)
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
            val minVersion = SemVer("v0.5.1", 0, 5, 1)
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
            val minVersion = SemVer("v0.9.1", 0, 9, 1)
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

    object Testmarks {
        val fetchBuildPlan = Testmark("fetchBuildPlan")
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

private val RUST_1_36: SemVer = SemVer.parseFromText("1.36.0")!!
