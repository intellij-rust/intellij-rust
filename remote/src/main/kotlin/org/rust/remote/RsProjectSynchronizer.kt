package org.rust.remote

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.remote.CredentialsType
import com.intellij.util.PathMappingSettings
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalDataBase
import org.rust.stdext.Result
import java.io.File
import java.util.function.Consumer

typealias PathMappings = List<PathMappingSettings.PathMapping>

interface RsProjectSynchronizer {
    val defaultRemotePath: String?
    fun getAutoMappings(): Result<PathMappings, String>? = null
    fun mapFilePath(project: Project, direction: RsSyncDirection, filePath: String): String?
    fun checkSynchronizationAvailable(syncCheckStrategy: RsSyncCheckStrategy): String?
    fun syncProject(module: Module, syncDirection: RsSyncDirection, callback: Consumer<Boolean>?, vararg fileNames: String)
}

interface RsProjectSynchronizerProvider {
    fun getSynchronizer(credsType: CredentialsType<*>, sdk: Sdk): RsProjectSynchronizer?

    companion object {
        val EP_NAME: ExtensionPointName<RsProjectSynchronizerProvider> =
            ExtensionPointName.create("org.rust.remote.projectSynchronizerProvider")

        fun find(credsType: CredentialsType<*>, sdk: Sdk): RsProjectSynchronizer? =
            EP_NAME.extensions.mapNotNull { it.getSynchronizer(credsType, sdk) }.firstOrNull()

        @JvmStatic
        fun getSynchronizer(sdk: Sdk): RsProjectSynchronizer? {
            val sdkAdditionalData = sdk.sdkAdditionalData
            if (sdkAdditionalData is RsRemoteSdkAdditionalDataBase) {
                return find(sdkAdditionalData.remoteConnectionType, sdk) ?: RsUnknownProjectSynchronizer
            }
            return null
        }
    }
}

interface RsSyncCheckStrategy

class RsSyncCheckOnly(val projectBaseDir: File) : RsSyncCheckStrategy

class RsSyncCheckCreateIfPossible(val module: Module, val remotePath: String?) : RsSyncCheckStrategy

enum class RsSyncDirection {
    LOCAL_TO_REMOTE,
    REMOTE_TO_LOCAL,
}
