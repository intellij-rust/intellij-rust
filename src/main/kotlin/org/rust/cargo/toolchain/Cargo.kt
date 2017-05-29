package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.CargoConstants.RUST_BACTRACE_ENV_VAR
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.utils.fullyRefreshDirectory
import java.io.File

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
    private val pathToCargoExecutable: String,
    private val pathToRustExecutable: String,
    // It's more convenient to use project directory rather then path to `Cargo.toml`
    // because some commands don't accept `--manifest-path` argument
    private val projectDirectory: String?,
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
        val hasAllFeatures = "--all-features" in generalCommand("metadata", listOf("--help")).execute(owner = owner).stdout
        val command = generalCommand("metadata", listOf("--verbose", "--format-version", "1")).apply {
            if (hasAllFeatures) addParameter("--all-features")
        }

        val output = command.execute(owner, listener)
        val rawData = parse(output.stdout)
        val projectDescriptionData = CargoMetadata.clean(rawData)
        return CargoWorkspace.deserialize(projectDescriptionData)
    }

    @Throws(ExecutionException::class)
    fun init(owner: Disposable, directory: VirtualFile) {
        val path = PathUtil.toSystemDependentName(directory.path)
        generalCommand("init", listOf("--bin", path)).execute(owner)
        check(File(directory.path, RustToolchain.Companion.CARGO_TOML).exists())
        fullyRefreshDirectory(directory)
    }

    fun reformatFile(owner: Disposable, filePath: String, listener: ProcessListener? = null) =
        rustfmtCommandline(filePath).execute(owner, listener)

    fun generalCommand(commandLine: CargoCommandLine): GeneralCommandLine {
        val env = when (commandLine.backtraceMode) {
            BacktraceMode.SHORT -> mapOf(RUST_BACTRACE_ENV_VAR to "short")
            BacktraceMode.FULL -> mapOf(RUST_BACTRACE_ENV_VAR to "full")
            else -> emptyMap()
        } + commandLine.environmentVariables

        val args = commandLine.additionalArguments.toMutableList()

        if (commandLine.command == "test" && commandLine.nocapture && "--nocapture" !in args) {
            if ("--" !in args) {
                args += "--"
            }
            args += "--nocapture"
        }

        return generalCommand(commandLine.command, args, env, commandLine.channel)
    }

    fun generalCommand(
        command: String,
        additionalArguments: List<String> = emptyList(),
        environmentVariables: Map<String, String> = emptyMap(),
        channel: RustChannel = RustChannel.DEFAULT
    ): GeneralCommandLine {
        val cmdLine = if (channel == RustChannel.DEFAULT) {
            GeneralCommandLine(pathToCargoExecutable)
        } else {
            if (rustup == null) error("Channel '$channel' cannot be set explicitly because rustup is not avaliable")
            rustup.createRunCommandLine(channel, pathToCargoExecutable)
        }

        cmdLine.withCharset(Charsets.UTF_8)
            .withWorkDirectory(projectDirectory)
            .withParameters(command)

        // Make output colored
        if (!SystemInfo.isWindows
            && command in COLOR_ACCEPTING_COMMANDS
            && additionalArguments.none { it.startsWith("--color") }) {

            cmdLine
                .withEnvironment("TERM", "ansi")
                .withRedirectErrorStream(true)
                .withParameters("--color=always") // Must come first in order not to corrupt the running program arguments
        }

        return cmdLine
            .withEnvironment(CargoConstants.RUSTC_ENV_VAR, pathToRustExecutable)
            .withEnvironment(environmentVariables)
            .withParameters(additionalArguments)
    }

    fun clippyCommandLine(channel: RustChannel): CargoCommandLine =
        CargoCommandLine("clippy", channel = channel)

    private fun rustfmtCommandline(filePath: String) =
        generalCommand("fmt").withParameters("--", "--write-mode=overwrite", "--skip-children", filePath)

    private fun GeneralCommandLine.execute(owner: Disposable, listener: ProcessListener? = null): ProcessOutput {
        val handler = CapturingProcessHandler(this)
        val cargoKiller = Disposable {
            // Don't attempt a graceful termination, Cargo can be SIGKILLed safely.
            // https://github.com/rust-lang/cargo/issues/3566
            handler.destroyProcess()
        }
        Disposer.register(owner, cargoKiller)

        listener?.let { handler.addProcessListener(it) }
        val output = try {
            handler.runProcess()
        } finally {
            Disposer.dispose(cargoKiller)
        }
        if (output.exitCode != 0) {
            throw ExecutionException("""
            Cargo execution failed (exit code ${output.exitCode}).
            $commandLineString
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
        } catch(e: JsonSyntaxException) {
            throw ExecutionException(e)
        }
    }

    private companion object {
        val COLOR_ACCEPTING_COMMANDS = listOf("bench", "build", "check", "clean", "clippy", "doc", "install", "publish", "run", "rustc", "test", "update")
    }
}
