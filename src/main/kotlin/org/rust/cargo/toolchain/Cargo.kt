/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PtyCommandLine
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.net.HttpConfigurable
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants.RUST_BACTRACE_ENV_VAR
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallClippy
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.ide.actions.InstallBinaryCrateAction
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.*
import org.rust.stdext.buildList
import java.nio.file.Path


/**
 * A main gateway for executing cargo commands.
 *
 * This class is not aware of SDKs or projects, so you'll need to provide
 * paths yourself.
 *
 * It is impossible to guarantee that paths to the project or executables are valid,
 * because the user can always just `rm ~/.cargo/bin -rf`.
 */
class Cargo(private val cargoExecutable: Path) {

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
            .execute(null)
            ?.stdoutLines
            ?.filterNot { it.startsWith(" ") }
            ?.map { BinaryCrate.from(it) }
            .orEmpty()

    fun installBinaryCrate(project: Project, crateName: String) {
        val cargoProject = project.cargoProjects.allProjects.firstOrNull() ?: return
        val commandLine = CargoCommandLine.forProject(cargoProject, "install", listOf("--force", crateName))
        commandLine.run(cargoProject, "Install $crateName")
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
    ): CargoWorkspace {
        val additionalArgs = mutableListOf("--verbose", "--format-version", "1", "--all-features")
        if (owner.rustSettings.useOffline) {
            additionalArgs += "-Zoffline"
        }

        val json = CargoCommandLine("metadata", projectDirectory, additionalArgs)
            .execute(owner, listener = listener)
            .stdout
            .dropWhile { it != '{' }
        val rawData = try {
            Gson().fromJson(json, CargoMetadata.Project::class.java)
        } catch (e: JsonSyntaxException) {
            throw ExecutionException(e)
        }
        val projectDescriptionData = CargoMetadata.clean(rawData)
        val manifestPath = projectDirectory.resolve("Cargo.toml")
        return CargoWorkspace.deserialize(manifestPath, projectDescriptionData)
    }

    @Throws(ExecutionException::class)
    fun init(
        owner: Disposable,
        directory: VirtualFile,
        createBinary: Boolean,
        vcs: String? = null
    ): GeneratedFilesHolder {
        val path = directory.pathAsPath
        val name = path.fileName.toString().replace(' ', '_')
        val crateType = if (createBinary) "--bin" else "--lib"

        val args = mutableListOf(crateType, "--name", name)

        vcs?.let {
            args.addAll(listOf("--vcs", vcs))
        }

        args.add(path.toString())

        CargoCommandLine("init", path, args).execute(owner)
        fullyRefreshDirectory(directory)

        val manifest = checkNotNull(directory.findChild(RustToolchain.CARGO_TOML)) { "Can't find the manifest file" }
        val fileName = if (createBinary) "main.rs" else "lib.rs"
        val sourceFiles = listOfNotNull(directory.findFileByRelativePath("src/$fileName"))
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
            if (settings.useOffline) add("-Zoffline")
            addAll(ParametersListUtil.parse(settings.externalLinterArguments))
        }

        val useClippy = settings.externalLinter == ExternalLinter.CLIPPY
            && !checkNeedInstallClippy(project, cargoProjectDirectory)
        val checkCommand = if (useClippy) "clippy" else "check"
        return CargoCommandLine(checkCommand, cargoProjectDirectory, arguments).execute(owner, ignoreExitCode = true)
    }

    fun toColoredCommandLine(commandLine: CargoCommandLine): GeneralCommandLine =
        toGeneralCommandLine(commandLine, colors = true)

    fun toGeneralCommandLine(commandLine: CargoCommandLine): GeneralCommandLine =
        toGeneralCommandLine(commandLine, colors = false)

    private fun toGeneralCommandLine(commandLine: CargoCommandLine, colors: Boolean): GeneralCommandLine =
        with(patchArgs(commandLine, colors)) {
            val parameters = buildList<String> {
                if (channel != RustChannel.DEFAULT) {
                    add("+$channel")
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
            )
        }

    @Throws(ExecutionException::class)
    private fun CargoCommandLine.execute(
        owner: Disposable,
        ignoreExitCode: Boolean = false,
        stdIn: ByteArray? = null,
        listener: ProcessListener? = null
    ): ProcessOutput = toGeneralCommandLine(this).execute(owner, ignoreExitCode, stdIn, listener)

    private var _http: HttpConfigurable? = null
    private val http: HttpConfigurable
        get() = _http ?: HttpConfigurable.getInstance()

    @TestOnly
    fun setHttp(http: HttpConfigurable) {
        _http = http
    }

    companion object {
        private val COLOR_ACCEPTING_COMMANDS: List<String> = listOf(
            "bench", "build", "check", "clean", "clippy", "doc", "install", "publish", "run", "rustc", "test", "update"
        )

        data class GeneratedFilesHolder(val manifest: VirtualFile, val sourceFiles: List<VirtualFile>)

        fun patchArgs(commandLine: CargoCommandLine, colors: Boolean): CargoCommandLine {
            val (pre, post) = commandLine.splitOnDoubleDash()
                .let { (pre, post) -> pre.toMutableList() to post.toMutableList() }

            if (commandLine.command == "test") {
                if (commandLine.allFeatures && !pre.contains("--all-features")) pre.add("--all-features")
                if (commandLine.nocapture && !pre.contains("--nocapture")) post.add(0, "--nocapture")
            }

            // Force colors
            val forceColors = colors
                && commandLine.command in COLOR_ACCEPTING_COMMANDS
                && commandLine.additionalArguments.none { it.startsWith("--color") }
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
                BacktraceMode.SHORT -> cmdLine.withEnvironment(RUST_BACTRACE_ENV_VAR, "short")
                BacktraceMode.FULL -> cmdLine.withEnvironment(RUST_BACTRACE_ENV_VAR, "full")
                BacktraceMode.NO -> Unit
            }
            environmentVariables.configureCommandLine(cmdLine, true)
            return if (emulateTerminal) {
                PtyCommandLine(cmdLine).withConsoleMode(false)
            } else {
                cmdLine
            }
        }

        fun checkNeedInstallCargoExpand(project: Project): Boolean {
            val minVersion = SemVer("v0.4.9", 0, 4, 9)
            return checkNeedInstallBinaryCrate(
                project,
                "cargo-expand",
                NotificationType.ERROR,
                "Need at least cargo-expand $minVersion",
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
            fun isNotInstalled(): Boolean {
                val cargo = project.toolchain?.rawCargo() ?: return false
                val installed = cargo.listInstalledBinaryCrates().any { (name, version) ->
                    name == crateName && (minVersion == null || version != null && version >= minVersion)
                }
                return !installed
            }

            val needInstall = if (ApplicationManager.getApplication().isDispatchThread) {
                project.computeWithCancelableProgress("Checking if $crateName is installed...", ::isNotInstalled)
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
