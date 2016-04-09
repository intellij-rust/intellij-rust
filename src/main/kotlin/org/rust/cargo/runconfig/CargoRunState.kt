package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import org.rust.cargo.commands.Cargo

class CargoRunState(environment: ExecutionEnvironment,
                    private val pathToCargo: String,
                    private val moduleDirectory: String,
                    private val command: String,
                    private val additionalArguments: List<String>,
                    private val environmentVariables: Map<String, String>) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val cmd = Cargo(pathToCargo, moduleDirectory).generalCommand(command, additionalArguments, environmentVariables)
        return OSProcessHandler(cmd)
    }
}
