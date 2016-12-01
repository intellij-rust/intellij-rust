package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.CargoProjectDescription
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
    private val projectDirectory: String?
) {
    /**
     * Fetch all dependencies and calculate project information.
     *
     * This is a potentially long running operation which can
     * legitimately fail due to network errors or inability
     * to resolve dependencies.
     */
    @Throws(ExecutionException::class)
    fun fullProjectDescription(listener: ProcessListener? = null): CargoProjectDescription {
        val hasAllFeatures = "--all-features" in generalCommand("metadata", listOf("--help")).execute().stdout
        val command = generalCommand("metadata", listOf("--verbose", "--format-version", "1")).apply {
            if (hasAllFeatures) addParameter("--all-features")
        }

        val output = command.execute(listener)
        val rawData = parse(output.stdout)
        val projectDescriptionData = CargoMetadata.clean(rawData)
        return CargoProjectDescription.deserialize(projectDescriptionData)
            ?: throw ExecutionException("Failed to understand cargo output:\n${output.stdout}\n${output.stderr}")
    }

    @Throws(ExecutionException::class)
    fun init(directory: VirtualFile) {
        val path = PathUtil.toSystemDependentName(directory.path)
        generalCommand("init", listOf("--bin", path)).execute()
        check(File(directory.path, RustToolchain.Companion.CARGO_TOML).exists())
        fullyRefreshDirectory(directory)
    }

    fun reformatFile(filePath: String, listener: ProcessListener? = null) =
        rustfmtCommandline(filePath).execute(listener)

    fun generalCommand(
        command: String,
        additionalArguments: List<String> = emptyList(),
        environmentVariables: Map<String, String> = emptyMap()
    ): GeneralCommandLine {
        var args = additionalArguments
        val cmdLine = GeneralCommandLine(pathToCargoExecutable)
            .withWorkDirectory(projectDirectory)
            .withParameters(command)
            .withEnvironment(CargoConstants.RUSTC_ENV_VAR, pathToRustExecutable)
            .withEnvironment(environmentVariables)

        // Make output colored
        if ((SystemInfo.isLinux || SystemInfo.isMac)
            && command in COLOR_ACCEPTING_COMMANDS
            && additionalArguments.none { it.startsWith("--color") }) {
            cmdLine
                .withEnvironment("TERM", "linux")
                .withRedirectErrorStream(true)
            args = additionalArguments + "--color=always"
        }
        return cmdLine.withParameters(args)
    }

    private fun rustfmtCommandline(filePath: String) =
        generalCommand("fmt").withParameters("--", "--write-mode=overwrite", "--skip-children", filePath)

    private fun GeneralCommandLine.execute(listener: ProcessListener? = null): ProcessOutput {
        val process = createProcess()
        val handler = CapturingProcessHandler(process, Charsets.UTF_8, commandLineString)

        listener?.let { handler.addProcessListener(it) }

        val output = handler.runProcess()
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
        val COLOR_ACCEPTING_COMMANDS = listOf("bench", "build", "clean", "doc", "install", "publish", "run", "test", "update")
    }
}
