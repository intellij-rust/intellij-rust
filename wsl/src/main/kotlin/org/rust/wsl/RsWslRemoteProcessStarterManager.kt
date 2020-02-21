package org.rust.wsl

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.project.Project
import org.rust.cargo.runconfig.RsProcessHandler
import org.rust.cargo.runconfig.remote.RsRemoteProcessStarterManager
import org.rust.ide.sdk.remote.RsRemotePathMapper
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalDataBase
import org.rust.stdext.Result

object RsWslRemoteProcessStarterManager : RsRemoteProcessStarterManager {

    override fun supports(sdkAdditionalData: RsRemoteSdkAdditionalDataBase): Boolean =
        sdkAdditionalData.isWsl

    override fun startRemoteProcess(
        project: Project?,
        commandLine: GeneralCommandLine,
        sdkAdditionalData: RsRemoteSdkAdditionalDataBase,
        pathMapper: RsRemotePathMapper
    ): RsProcessHandler = startWslProcess(project, commandLine, sdkAdditionalData, false)

    override fun executeRemoteProcess(
        project: Project?,
        command: Array<String>,
        workingDir: String?,
        sdkAdditionalData: RsRemoteSdkAdditionalDataBase,
        pathMapper: RsRemotePathMapper
    ): ProcessOutput {
        val distribution = sdkAdditionalData.getDistribution().let {
            when (it) {
                is Result.Success -> it.result
                is Result.Failure -> throw ExecutionException(it.error)
            }
        }
        val localDir = workingDir?.let { distribution.getWindowsPath(it) } ?: RsHelpersLocator.getHelpersRoot().path
        val commandLine = GeneralCommandLine(*command).withWorkDirectory(localDir)
        val process = startRemoteProcess(project, commandLine, sdkAdditionalData, pathMapper)
        return CapturingProcessRunner(process).runProcess()
    }
}
