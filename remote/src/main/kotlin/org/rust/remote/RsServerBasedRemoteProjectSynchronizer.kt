/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.remote

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import org.rust.ide.sdk.remote.RsRemoteSdkAdditionalData
import org.rust.ide.sdk.remote.RsRemoteSdkCredentials
import org.rust.stdext.Result

abstract class RsServerBasedRemoteProjectSynchronizer(protected val sdk: Sdk) : RsProjectSynchronizer {
    protected val additionalData: RsRemoteSdkAdditionalData = getAdditionalData(sdk)

    protected val credentials: RsRemoteSdkCredentials
        protected get() = additionalData.getRemoteSdkCredentials(null, false)

    override val defaultRemotePath: String? = null

    override fun mapFilePath(project: Project, direction: RsSyncDirection, filePath: String): String? {
        val mappings = getPathMappingsIgnoreError(project) ?: return null
        for (mapping in mappings) {
            when {
                direction === RsSyncDirection.REMOTE_TO_LOCAL && mapping.canReplaceRemote(filePath) ->
                    return mapping.mapToLocal(filePath)
                direction === RsSyncDirection.LOCAL_TO_REMOTE && mapping.canReplaceLocal(filePath) ->
                    return mapping.mapToRemote(filePath)
            }
        }
        return null
    }

    protected abstract fun getPathMappings(project: Project): Result<PathMappings, String>?

    protected fun getPathMappingsIgnoreError(project: Project): PathMappings? =
        getPathMappings(project)?.successOrNull

    companion object {
        fun getAdditionalData(sdk: Sdk): RsRemoteSdkAdditionalData {
            val data = sdk.sdkAdditionalData
            assert(data is RsRemoteSdkAdditionalData) { "sdk is not remote: $sdk" }
            return data as RsRemoteSdkAdditionalData
        }
    }
}
