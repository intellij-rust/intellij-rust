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
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants.RUST_BACTRACE_ENV_VAR
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.Rustup.Companion.checkNeedInstallClippy
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.util.DownloadResult
import org.rust.ide.actions.InstallCargoPackageAction
import org.rust.ide.notifications.showBalloon
import org.rust.openapiext.*
import org.rust.stdext.buildList
import java.io.File
import java.nio.file.Path


private val LOG = Logger.getInstance(Cargo::class.java)

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
    fun checkSupportForBuildCheckAllTargets(): Boolean {
        val lines = GeneralCommandLine(cargoExecutable)
            .withParameters("help", "check")
            .execute()
            ?.stdoutLines
            ?: return false

        return lines.any { it.contains(" --all-targets ") }
    }

    fun listPackages(): List<String> =
        GeneralCommandLine(cargoExecutable)
            .withParameters("install", "--list")
            .execute(null)
            ?.stdoutLines
            ?: emptyList()

    fun installPackage(owner: Disposable, packageName: String): DownloadResult<Unit> =
        try {
            GeneralCommandLine(cargoExecutable)
                .withParameters("install", packageName)
                .execute(owner, false)
            DownloadResult.Ok(Unit)
        } catch (e: ExecutionException) {
            val message = "cargo failed: `${e.message}`"
            LOG.warn(message)
            DownloadResult.Err(message)
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
    fun fullProjectDescription(owner: Project, projectDirectory: Path, listener: ProcessListener? = null): CargoWorkspace {
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
    fun init(owner: Disposable, directory: VirtualFile, createBinary: Boolean) {
        val path = directory.pathAsPath
        val name = path.fileName.toString().replace(' ', '_')
        val crateType = if (createBinary) "--bin" else "--lib"
        CargoCommandLine(
            "init", path,
            listOf(crateType, "--name", name, path.toString())
        ).execute(owner)
        check(File(directory.path, RustToolchain.CARGO_TOML).exists())
        fullyRefreshDirectory(directory)
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
            return cmdLine
        }

        @Suppress("SameParameterValue")
        private fun checkNeedInstallPackage(
            project: Project,
            packageName: String,
            notificationType: NotificationType,
            message: String? = null
        ): Boolean {
            fun isNotInstalled(): Boolean {
                val cargo = project.toolchain?.rawCargo() ?: return false
                val installed = cargo.listPackages().any { it.startsWith(packageName) }
                return !installed
            }

            val needInstall = if (ApplicationManager.getApplication().isDispatchThread) {
                project.computeWithCancelableProgress("Checking if $packageName is installed...", ::isNotInstalled)
            } else {
                isNotInstalled()
            }

            if (needInstall) {
                project.showBalloon(
                    "<code>$packageName</code> is not installed",
                    message ?: "",
                    notificationType,
                    InstallCargoPackageAction(packageName)
                )
            }

            return needInstall
        }
    }
}
