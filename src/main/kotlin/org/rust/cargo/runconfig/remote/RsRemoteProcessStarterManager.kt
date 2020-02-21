package org.rust.cargo.runconfig.remote

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.rust.ide.sdk.remote.RsRemotePathMapper
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalDataBase

interface RsRemoteProcessStarterManager {

    fun supports(sdkAdditionalData: RsRemoteSdkAdditionalDataBase): Boolean

    fun startRemoteProcess(
        project: Project?,
        commandLine: GeneralCommandLine,
        sdkAdditionalData: RsRemoteSdkAdditionalDataBase,
        pathMapper: RsRemotePathMapper
    ): ProcessHandler

    fun executeRemoteProcess(
        project: Project?,
        command: Array<String>,
        workingDir: String?,
        sdkAdditionalData: RsRemoteSdkAdditionalDataBase,
        pathMapper: RsRemotePathMapper
    ): ProcessOutput

    companion object {
        val EP_NAME: ExtensionPointName<RsRemoteProcessStarterManager> =
            ExtensionPointName.create<RsRemoteProcessStarterManager>("org.rust.remoteProcessStarterManager")

        fun getManager(pyRemoteSdkAdditionalDataBase: RsRemoteSdkAdditionalDataBase): RsRemoteProcessStarterManager {
            for (processManager in EP_NAME.extensions) {
                if (processManager.supports(pyRemoteSdkAdditionalDataBase)) {
                    return processManager
                }
            }
            throw error("Unsupported Rust SDK type")
        }
    }
}
