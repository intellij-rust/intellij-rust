package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.projectRoots.Sdk
import org.rust.cargo.project.cargoCommandLine

class CargoRunState(environment: ExecutionEnvironment,
                    private val sdk: Sdk,
                    // TODO: use path to Cargo.toml here
                    private val workDirectory: String,
                    private val command: String,
                    private val additionalArguments: List<String>) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val cmd = sdk.cargoCommandLine(command)
            .withParameters(additionalArguments)
            .withWorkDirectory(workDirectory)

        return OSProcessHandler(cmd)
    }
}
