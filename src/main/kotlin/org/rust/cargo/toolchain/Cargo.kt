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
import com.intellij.util.net.HttpConfigurable
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConstants
import org.rust.cargo.CargoConstants.RUST_BACTRACE_ENV_VAR
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.openapiext.GeneralCommandLine
import org.rust.openapiext.fullyRefreshDirectory
import org.rust.openapiext.pathAsPath
import org.rust.openapiext.withWorkDirectory
import org.rust.stdext.buildList
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
    private val rustExecutable: Path
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
    fun fullProjectDescription(owner: Disposable, projectDirectory: Path, listener: ProcessListener? = null): CargoWorkspace {
        val json = CargoCommandLine("metadata", projectDirectory,
            listOf("--verbose", "--format-version", "1", "--all-features")
        ).execute(owner, listener)
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
        check(File(directory.path, RustToolchain.Companion.CARGO_TOML).exists())
        fullyRefreshDirectory(directory)
    }

    @Throws(ExecutionException::class)
    fun reformatFile(owner: Disposable, file: VirtualFile, listener: ProcessListener? = null): ProcessOutput {
        val cmd = CargoCommandLine(
            "fmt", file.parent.pathAsPath,
            listOf("--all", "--", "--write-mode=overwrite", "--skip-children", file.path)
        )
        val result = cmd.execute(owner, listener)
        VfsUtil.markDirtyAndRefresh(true, true, true, file)
        return result
    }

    @Throws(ExecutionException::class)
    fun checkProject(owner: Disposable, projectDirectory: Path): ProcessOutput =
        CargoCommandLine("check", projectDirectory, listOf("--message-format=json", "--all"))
            .execute(owner, ignoreExitCode = true)

    fun toColoredCommandLine(commandLine: CargoCommandLine): GeneralCommandLine =
        generalCommandLine(commandLine, true)

    fun toGeneralCommandLine(commandLine: CargoCommandLine): GeneralCommandLine =
        generalCommandLine(commandLine, false)

    private fun generalCommandLine(commandLine: CargoCommandLine, colors: Boolean): GeneralCommandLine {
        @Suppress("NAME_SHADOWING")
        val commandLine = if (commandLine.command == "test" && commandLine.nocapture) {
            commandLine.withDoubleDashFlag("--nocapture")
        } else {
            commandLine
        }

        val cmdLine = GeneralCommandLine(cargoExecutable)
            .withCharset(Charsets.UTF_8)
            .withWorkDirectory(commandLine.workingDirectory)
            .withEnvironment("TERM", "ansi")
            .withRedirectErrorStream(true)

        withProxyIfNeeded(cmdLine, http)

        when (commandLine.backtraceMode) {
            BacktraceMode.SHORT -> cmdLine.withEnvironment(RUST_BACTRACE_ENV_VAR, "short")
            BacktraceMode.FULL -> cmdLine.withEnvironment(RUST_BACTRACE_ENV_VAR, "full")
            BacktraceMode.NO -> Unit
        }
        commandLine.environmentVariables.configureCommandLine(cmdLine, true)

        // Force colors
        val forceColors = colors
            // Hey, wanna repeat https://github.com/rust-lang/cargo/pull/4162 for rustc,
            // so that we can enable colors on windows?
            && !SystemInfo.isWindows
            && commandLine.command in COLOR_ACCEPTING_COMMANDS
            && commandLine.additionalArguments.none { it.startsWith("--color") }

        val parameters = buildList<String> {
            if (commandLine.channel != RustChannel.DEFAULT) {
                add("+${commandLine.channel}")
            }
            add(commandLine.command)
            if (forceColors) add("--color=always")
            addAll(commandLine.additionalArguments)
        }

        return cmdLine.withParameters(parameters)
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
