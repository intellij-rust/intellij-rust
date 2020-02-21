package org.rust.wsl

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.CredentialsType
import com.intellij.wsl.WSLCredentialsType
import org.rust.remote.*
import org.rust.stdext.Result
import java.util.function.Consumer

class RsWslProjectSynchronizerProvider : RsProjectSynchronizerProvider {
    override fun getSynchronizer(credsType: CredentialsType<*>, sdk: Sdk): RsProjectSynchronizer? =
        if (credsType is WSLCredentialsType) RsWslProjectSynchronizer(sdk) else null
}

class RsWslProjectSynchronizer(sdk: Sdk) : RsServerBasedRemoteProjectSynchronizer(sdk) {

    override fun getPathMappings(project: Project): Result<PathMappings, String> =
        getAutoMappings()

    override fun getAutoMappings(): Result<PathMappings, String> =
        additionalData.getDistribution().map(WSLDistribution::rootMappings)

    override fun checkSynchronizationAvailable(syncCheckStrategy: RsSyncCheckStrategy): String? =
        when (val distribution = additionalData.getDistribution()) {
            is Result.Success -> null
            is Result.Failure -> distribution.error
        }

    override fun syncProject(
        module: Module,
        syncDirection: RsSyncDirection,
        callback: Consumer<Boolean>?,
        vararg fileNames: String
    ) {
        callback?.accept(true)
    }
}
