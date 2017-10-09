/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants
import org.rust.cargo.CargoConstants.RUST_BACTRACE_ENV_VAR
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.fullyRefreshDirectory
import org.rust.openapiext.withWorkDirectory
import java.io.File
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
class Cargo(
    private val cargoExecutable: Path,
    private val rustExecutable: Path,
    // It's more convenient to use project directory rather then path to `Cargo.toml`
    // because some commands don't accept `--manifest-path` argument
    private val projectDirectory: Path?,
    private val rustup: Rustup?
) {

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
    fun fullProjectDescription(owner: Disposable, listener: ProcessListener? = null): CargoWorkspace {
        val output = CargoCommandLine("metadata", listOf("--verbose", "--format-version", "1", "--all-features"))
            .execute(owner, listener)
        val rawData = parse(output.stdout)
        val projectDescriptionData = CargoMetadata.clean(rawData)
        val manifestPath = projectDirectory?.resolve("Cargo.toml")
        return CargoWorkspace.deserialize(manifestPath, projectDescriptionData)
    }

    @Throws(ExecutionException::class)
    fun init(owner: Disposable, directory: VirtualFile, createExecutableModule: Boolean) {
        val path = PathUtil.toSystemDependentName(directory.path)
        val moduleTypeOption = if (createExecutableModule) "--bin" else "--lib"
        CargoCommandLine("init", listOf(moduleTypeOption, path))
            .execute(owner)
        check(File(directory.path, RustToolchain.Companion.CARGO_TOML).exists())
        fullyRefreshDirectory(directory)
    }

    fun reformatFile(owner: Disposable, file: VirtualFile, listener: ProcessListener? = null): ProcessOutput {
        val cmd = CargoCommandLine(
            "fmt",
            listOf("--all", "--", "--write-mode=overwrite", "--skip-children", file.path)
        )
        val result = cmd.execute(owner, listener)
        VfsUtil.markDirtyAndRefresh(true, true, true, file)
        return result
    }

    fun checkProject(owner: Disposable): ProcessOutput =
        CargoCommandLine("check", listOf("--message-format=json", "--all"))
            .execute(owner, ignoreExitCode = true)

    fun clippyCommandLine(channel: RustChannel): CargoCommandLine =
        CargoCommandLine("clippy", channel = channel)

    fun toColoredCommandLine(commandLine: CargoCommandLine): GeneralCommandLine =
        generalCommandLine(commandLine, true)

    fun toGeneralCommandLine(commandLine: CargoCommandLine): GeneralCommandLine =
        generalCommandLine(commandLine, false)

    private fun generalCommandLine(commandLine: CargoCommandLine, colors: Boolean): GeneralCommandLine {
        val cmdLine = when {
            commandLine.channel == RustChannel.DEFAULT -> GeneralCommandLine(cargoExecutable)
            rustup == null -> error("Channel cannot be set because rustup is not available")
            else -> GeneralCommandLine(cargoExecutable, "+${commandLine.channel}")
        }

        cmdLine
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(commandLine.workingDirectory ?: projectDirectory)
            .withParameters(commandLine.command)
            .withEnvironment(CargoConstants.RUSTC_ENV_VAR, rustExecutable.toString())
            .withEnvironment("TERM", "ansi")
            .withRedirectErrorStream(true)

        withProxyIfNeeded(cmdLine, http)

        when (commandLine.backtraceMode) {
            BacktraceMode.SHORT -> cmdLine.withEnvironment(RUST_BACTRACE_ENV_VAR, "short")
            BacktraceMode.FULL -> cmdLine.withEnvironment(RUST_BACTRACE_ENV_VAR, "full")
            BacktraceMode.NO -> {
            }
        }
        commandLine.environmentVariables.configureCommandLine(cmdLine, true)

        val args: List<String> = run {
            val args = commandLine.additionalArguments.toMutableList()
            if (commandLine.command == "test" && commandLine.nocapture && "--nocapture" !in args) {
                if ("--" !in args) {
                    args += "--"
                }
                args += "--nocapture"
            }
            args
        }

        // Force colors
        if (colors
            && !SystemInfo.isWindows //BACKCOMPAT: remove windows check once termcolor'ed Cargo is stable
            && commandLine.command in COLOR_ACCEPTING_COMMANDS
            && args.none { it.startsWith("--color") }) {

            cmdLine
                .withParameters("--color=always") // Must come first in order not to corrupt the running program arguments
        }

        return cmdLine.withParameters(args)
    }


    private fun CargoCommandLine.execute(owner: Disposable, listener: ProcessListener? = null,
                                         ignoreExitCode: Boolean = false): ProcessOutput {
        val command = toGeneralCommandLine(this)
        val handler = CapturingProcessHandler(command)
        val cargoKiller = Disposable {
            // Don't attempt a graceful termination, Cargo can be SIGKILLed safely.
            // https://github.com/rust-lang/cargo/issues/3566
            handler.destroyProcess()
        }

        val alreadyDisposed = runReadAction {
            if (Disposer.isDisposed(owner)) {
                true
            } else {
                Disposer.register(owner, cargoKiller)
                false
            }
        }

        if (alreadyDisposed) {
            // On the one hand, this seems fishy,
            // on the other hand, this is isomorphic
            // to the scenario where cargoKiller triggers.
            if (ignoreExitCode) {
                return ProcessOutput().apply { setCancelled() }
            } else {
                throw ExecutionException("Cargo command failed to start")
            }
        }

        listener?.let { handler.addProcessListener(it) }
        val output = try {
            handler.runProcess()
        } finally {
            Disposer.dispose(cargoKiller)
        }
        if (!ignoreExitCode && output.exitCode != 0) {
            throw ExecutionException("""
            Cargo execution failed (exit code ${output.exitCode}).
            ${command.commandLineString}
            stdout : ${output.stdout}
            stderr : ${output.stderr}""".trimIndent())
        }
        return output
    }

    private fun parse(output: String): CargoMetadata.Project {
        // Skip "Downloading..." stuff
        val json = output.dropWhile { it != '{' }
        return try {
            Gson().fromJson(json, CargoMetadata.Project::class.java)
        } catch (e: JsonSyntaxException) {
            throw ExecutionException(e)
        }
    }

    private var _http: HttpConfigurable? = null
    private val http: HttpConfigurable
        get() = _http ?: HttpConfigurable.getInstance()

    @TestOnly
    fun setHttp(http: HttpConfigurable) {
        _http = http
    }

    private companion object {
        val COLOR_ACCEPTING_COMMANDS = listOf(
            "bench", "build", "check", "clean", "clippy", "doc", "install", "publish", "run", "rustc", "test", "update"
        )
    }
}
