package org.rust.wsl

import com.intellij.openapi.project.Project
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalDataBase
import org.rust.ide.sdk.remote.RsRemoteSocketToLocalHostProvider
import org.rust.stdext.Result

class RsWslSocketProvider(
    private val project: Project?,
    private val additionalData: RsRemoteSdkAdditionalDataBase
) : RsRemoteSocketToLocalHostProvider {
    override fun getRemoteSocket(localPort: Int): Pair<String, Int> {
        if (isWsl1(project, additionalData)) {
            return "127.0.0.1" to localPort
        }
        return when (val wsl2WindowsIp = getWsl2WindowsIp(project, additionalData)) {
            is Result.Success -> wsl2WindowsIp.result.winIp to localPort
            is Result.Failure -> error(wsl2WindowsIp.error)
        }
    }
}
