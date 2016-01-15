package org.rust.cargo.runconfig

import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import org.rust.cargo.project.cargoCommandLine

class RustRunState(environment: ExecutionEnvironment,
                   private val module: Module,
                   private val isRelease: Boolean) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val sdk = ModuleRootManager.getInstance(module).sdk!!
        val cmd = sdk.cargoCommandLine
            .withParameters("run")
            // FIXME: What is the correct way to get module's root directory?
            .withWorkDirectory(module.moduleFile?.parent?.path)
        if (isRelease) {
            cmd.addParameter("--release")
        }
        return OSProcessHandler(cmd)
    }
}
