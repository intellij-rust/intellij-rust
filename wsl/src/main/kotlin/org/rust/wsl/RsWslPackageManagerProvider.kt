package org.rust.wsl

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessRunner
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.wsl.WSLCredentialsHolder
import org.rust.ide.packaging.RsPackageManager
import org.rust.ide.packaging.RsPackageManagerBase
import org.rust.ide.packaging.RsPackageManagerProvider

class RsWslPackageManagerProvider : RsPackageManagerProvider {
    override fun tryCreateForSdk(sdk: Sdk): RsPackageManager? {
        val data = sdk.sdkAdditionalData as? RsRemoteSdkAdditionalData ?: return null
        val holder = data.connectionCredentials().credentials as? WSLCredentialsHolder ?: return null
        return RsWslPackageManager(holder, sdk, data)
    }
}

private class RsWslPackageManager(
    private val holder: WSLCredentialsHolder,
    sdk: Sdk,
    private val data: RsRemoteSdkAdditionalData
) : RsPackageManagerBase(sdk) {

    init {
        separator = "/"
    }

    override fun getHelperPath(helper: String): String? =
        holder.distribution?.getWslPath(RsHelpersLocator.getHelperPath(helper))

    override fun getRustProcessOutput(
        helperPath: String,
        args: List<String>,
        askForSudo: Boolean,
        showProgress: Boolean,
        workingDir: String?
    ): ProcessOutput {
        val command = listOf(data.interpreterPath, helperPath) + args
        val wslProcess = startWslProcess(null, GeneralCommandLine(command), data, askForSudo, workingDir)
        return CapturingProcessRunner(wslProcess).runProcess()
    }

    override fun toSystemDependentName(dirName: String): String = dirName
}
