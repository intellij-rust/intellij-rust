/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.Testmark
import com.intellij.openapiext.isDispatchThread
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants.RUST_BACKTRACE_ENV_VAR
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.command.CargoCommandConfiguration.Companion.findCargoProject
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallClippy
import org.rust.cargo.toolchain.impl.BuildScriptsInfo
import org.rust.cargo.toolchain.impl.CargoBuildPlan
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.ide.actions.InstallBinaryCrateAction
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.*
import org.rust.stdext.buildList
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


/**
 * A main gateway for executing cargo commands.
 *
 * This class is not aware of SDKs or projects, so you'll need to provide
 * paths yourself.
 *
 * It is impossible to guarantee that paths to the project or executables are valid,
 * because the user can always just `rm ~/.cargo/bin -rf`.
 */
class Cargo(private val cargoExecutable: Path, private val rustcExecutable: Path) {

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
        GeneralCommandLine(cargoExecutable)
            .withParameters("install", "--list")
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
        val lines = GeneralCommandLine(cargoExecutable)
            .withParameters("help", "check")
            .execute()
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
        listener: ProcessListener? = null
    ): CargoWorkspaceData {
        val rawData = fetchMetadata(owner, projectDirectory, listener)
        val buildScriptsInfo = fetchBuildScriptsInfo(owner, projectDirectory, listener)
        val buildPlan = if (buildScriptsInfo?.containsOutDirInfo != true) {
            fetchBuildPlan(owner, projectDirectory, listener)
        } else {
            null
        }

        return CargoMetadata.clean(rawData, buildScriptsInfo, buildPlan)
    }

    @Throws(ExecutionException::class)
    private fun fetchMetadata(
        owner: Project,
        projectDirectory: Path,
        listener: ProcessListener?
    ): CargoMetadata.Project {
        val additionalArgs = mutableListOf("--verbose", "--format-version", "1", "--all-features")
        val json = CargoCommandLine("metadata", projectDirectory, additionalArgs)
            .execute(owner, listener = listener)
            .stdout
            .dropWhile { it != '{' }
        val project = try {
            Gson().fromJson(json, CargoMetadata.Project::class.java)
        } catch (e: JsonSyntaxException) {
            throw ExecutionException(e)
        }

        val workspaceRoot = project.workspace_root

        if (projectDirectory.toString() == workspaceRoot) {
            return project
        }

        val workspaceRootPath = Paths.get(workspaceRoot)

        // If the selected projectDirectory doesn't resolve directly to the directory that Cargo spat out at us,
        // then there's something a bit special with the cargo workspace, and we don't want to assume anything.
        if (!Files.isSameFile(projectDirectory, workspaceRootPath)) {
            return project
        }

        // Otherwise, it's just a normal symlink.

        val normalisedWorkspace = projectDirectory.normalize().toString()

        return project.copy(workspace_root = normalisedWorkspace)
    }

    private fun fetchBuildScriptsInfo(
        owner: Project,
        projectDirectory: Path,
        listener: ProcessListener?
    ): BuildScriptsInfo? {
        if (!isFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS)) return null
        val additionalArgs = listOf("--message-format", "json")
        val commandLine = CargoCommandLine("check", projectDirectory, additionalArgs)

        val processOutput = try {
            commandLine.execute(owner, listener = listener)
        } catch (e: ExecutionException) {
            LOG.warn(e)
            return null
        }

        val messages = mutableMapOf<PackageId, BuildScriptMessage>()

        for (line in processOutput.stdoutLines) {
            val jsonObject = try {
                JsonParser.parseString(line).asJsonObject
            } catch (ignore: JsonSyntaxException) {
                continue
            }
            val message = BuildScriptMessage.fromJson(jsonObject) ?: continue
            messages[message.package_id] = message
        }
        return BuildScriptsInfo(messages)
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

        val manifest = checkNotNull(directory.findChild(RustToolchain.CARGO_TOML)) { "Can't find the manifest file" }
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

        val manifest = checkNotNull(directory.findChild(RustToolchain.CARGO_TOML)) { "Can't find the manifest file" }
        val sourceFiles = listOf("main", "lib").mapNotNull { directory.findFileByRelativePath("src/${it}.rs") }
        return GeneratedFilesHolder(manifest, sourceFiles)
    }

    @Throws(ExecutionException::class)
    fun checkProject(
        project: Project,
        owner: Disposable,
        cargoProjectDirectory: Path,
        cargoPackageName: String? = null
    ): ProcessOutput {
        val settings = project.rustSettings
        val arguments = buildList<String> {
            add("--message-format=json")

            if (cargoPackageName != null) {
                add("--package")
                add(cargoPackageName)
            } else {
                add("--all")
            }

            if (settings.compileAllTargets && checkSupportForBuildCheckAllTargets()) add("--all-targets")
            addAll(ParametersListUtil.parse(settings.externalLinterArguments))
        }

        val useClippy = settings.externalLinter == ExternalLinter.CLIPPY
            && !checkNeedInstallClippy(project, cargoProjectDirectory)
        val checkCommand = if (useClippy) "clippy" else "check"
        return CargoCommandLine(checkCommand, cargoProjectDirectory, arguments)
            .execute(project, owner, ignoreExitCode = true)
    }

    fun toColoredCommandLine(project: Project, commandLine: CargoCommandLine): GeneralCommandLine =
        toGeneralCommandLine(project, commandLine, colors = true)

    fun toGeneralCommandLine(project: Project, commandLine: CargoCommandLine): GeneralCommandLine =
        toGeneralCommandLine(project, commandLine, colors = false)

    private fun toGeneralCommandLine(project: Project, commandLine: CargoCommandLine, colors: Boolean): GeneralCommandLine =
        with(patchArgs(commandLine, colors)) {
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
            createGeneralCommandLine(
                cargoExecutable,
                workingDirectory,
                backtraceMode,
                environmentVariables,
                parameters,
                emulateTerminal,
                http
            ).withEnvironment("RUSTC", rustcExecutable.toString())
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
        GeneralCommandLine(cargoExecutable)
            .withParameters(listOf("install", "cargo-generate"))
            .execute(owner, listener = listener)
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

        private val COLOR_ACCEPTING_COMMANDS: List<String> = listOf(
            "bench", "build", "check", "clean", "clippy", "doc", "install", "publish", "run", "rustc", "test", "update"
        )

        /** Environment variable to unlock unstable features of rustc and cargo.
         *  It doesn't change real toolchain.
         *
         * @see <a href="https://github.com/rust-lang/cargo/blob/06ddf3557796038fd87743bd3b6530676e12e719/src/cargo/core/features.rs#L447">features.rs</a>
         */
        private const val RUSTC_BOOTSTRAP: String = "RUSTC_BOOTSTRAP"

        data class GeneratedFilesHolder(val manifest: VirtualFile, val sourceFiles: List<VirtualFile>)

        val cargoCommonPatch: CargoPatch = { patchArgs(it, true) }

        fun patchArgs(commandLine: CargoCommandLine, colors: Boolean): CargoCommandLine {
            val (pre, post) = commandLine.splitOnDoubleDash()
                .let { (pre, post) -> pre.toMutableList() to post.toMutableList() }

            if (commandLine.command == "test" && commandLine.allFeatures && !pre.contains("--all-features")) {
                pre.add("--all-features")
            }

            // Force colors
            val forceColors = colors &&
                commandLine.command in COLOR_ACCEPTING_COMMANDS &&
                commandLine.additionalArguments.none { it.startsWith("--color") }
            if (forceColors) pre.add(0, "--color=always")

            return commandLine.copy(additionalArguments = if (post.isEmpty()) pre else pre + "--" + post)
        }

        fun createGeneralCommandLine(
            executablePath: Path,
            workingDirectory: Path,
            backtraceMode: BacktraceMode,
            environmentVariables: EnvironmentVariablesData,
            parameters: List<String>,
            emulateTerminal: Boolean,
            http: HttpConfigurable = HttpConfigurable.getInstance()
        ): GeneralCommandLine {
            val cmdLine = GeneralCommandLine(executablePath)
                .withWorkDirectory(workingDirectory)
                .withEnvironment("TERM", "ansi")
                .withRedirectErrorStream(true)
                .withParameters(parameters)
                // Explicitly use UTF-8.
                // Even though default system encoding is usually not UTF-8 on Windows,
                // most Rust programs are UTF-8 only.
                .withCharset(Charsets.UTF_8)
            withProxyIfNeeded(cmdLine, http)
            when (backtraceMode) {
                BacktraceMode.SHORT -> cmdLine.withEnvironment(RUST_BACKTRACE_ENV_VAR, "short")
                BacktraceMode.FULL -> cmdLine.withEnvironment(RUST_BACKTRACE_ENV_VAR, "full")
                BacktraceMode.NO -> Unit
            }
            environmentVariables.configureCommandLine(cmdLine, true)
            return if (emulateTerminal) {
                if (!SystemInfo.isWindows) {
                    cmdLine.environment["TERM"] = "xterm-256color"
                }
                PtyCommandLine(cmdLine).withInitialColumns(PtyCommandLine.MAX_COLUMNS)
            } else {
                cmdLine
            }
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
            val cargo = project.toolchain?.rawCargo() ?: return false
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

private val RUST_1_36: SemVer = SemVer.parseFromText("1.36.0")!!
