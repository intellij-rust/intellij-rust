package org.rust.cargo.commands

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.util.io.FileUtil
import org.rust.cargo.CargoConstants
import org.rust.cargo.commands.impl.Project
import java.io.File


/**
 * A main gateway for executing cargo commands.
 *
 * This class is not aware of SDKs or projects, so you'll need to provide
 * paths yourself.
 */
class Cargo(
    private val pathToCargoExecutable: String,
    private val pathToManifest: String
) {
    init {
        require(File(pathToCargoExecutable).canExecute())
        require(File(pathToManifest).canRead())
    }

    /**
     * Fetch all dependencies and calculate project information.
     *
     * This is a potentially long running operation which can
     * legitimately fail due to network errors or inability
     * to resolve dependencies.
     */
    @Throws(ExecutionException::class)
    fun fullProjectDescription(listener: ProcessListener? = null): CargoProjectDescription {
        val output = metadataCommandline.execute(listener)
        val data = parse(output)
        return CargoProjectDescription(data)
    }

    companion object {
        fun fromProjectDirectory(pathToCargoExecutable: String, projectDir: String): Cargo =
            Cargo(pathToCargoExecutable, FileUtil.join(projectDir, CargoConstants.MANIFEST_FILE))
    }

    private val metadataCommandline: GeneralCommandLine get() =
        GeneralCommandLine(pathToCargoExecutable)
            .withParameters("metadata")
            .withParameters("--manifest-path", pathToManifest)

    private fun GeneralCommandLine.execute(listener: ProcessListener? = null): ProcessOutput {
        val process = createProcess()
        val handler = CapturingProcessHandler(process)

        listener?.let { handler.addProcessListener(it) }

        val output =  handler.runProcess()
        if (output.exitCode != 0) {
            // We need at least `0.9.0-nightly (6c05bcb 2016-01-29)` cargo
            // Remove this when `cargo metadata` hits stable
            val noSubcommand = output.stderr.toLowerCase().contains("no such subcommand")
            val message = if (noSubcommand) {
                "No ${parametersList[0]} subcommand, please update cargo"
            } else {
                "Cargo execution failed." +
                    "\ncommand: $commandLineString" +
                    "\ncode : ${output.exitCode}" +
                    "\nstdout: ${output.stdout}" +
                    "\nstderr: ${output.stderr}"
            }
            throw ExecutionException(message)
        }
        return output
    }

    private fun parse(output: ProcessOutput): Project {
        // Skip "Downloading..." stuff
        val json = output.stdout.dropWhile { it != '{' }
        return try {
            Gson().fromJson(json, Project::class.java)
        } catch(e: JsonSyntaxException) {
           throw ExecutionException(e)
        }
    }
}
