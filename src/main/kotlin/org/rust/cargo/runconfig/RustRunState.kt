package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.projectRoots.Sdk
import org.rust.cargo.project.cargoCommandLine

class RustRunState(environment: ExecutionEnvironment,
                   private val sdk: Sdk,
                   // TODO: use path to Cargo.toml here
                   private val workDirectory: String,
                   private val isRelease: Boolean) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val cmd = sdk.cargoCommandLine
            .withParameters("run")
            .withWorkDirectory(workDirectory)
        if (isRelease) {
            cmd.addParameter("--release")
        }
        return OSProcessHandler(cmd)
    }
}
