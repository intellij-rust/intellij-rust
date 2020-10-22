/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk.remote

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.remote.*
import com.intellij.remote.ext.CredentialsCase
import com.intellij.remote.ext.CredentialsManager
import com.intellij.util.Consumer
import org.jdom.Element
import org.rust.ide.sdk.RsSdkAdditionalData

class RsRemoteSdkAdditionalData private constructor(
    remoteToolchainPath: String,
    private val remoteSdkProperties: RemoteSdkPropertiesHolder
) : RsSdkAdditionalData(),
    RemoteSdkProperties by remoteSdkProperties,
    RemoteSdkAdditionalData<RsRemoteSdkCredentials> {
    private val remoteConnectionCredentialsWrapper = RemoteConnectionCredentialsWrapper()

    val presentableDetails: String
        get() = remoteConnectionCredentialsWrapper.getPresentableDetails(remoteSdkProperties.interpreterPath)

    init {
        interpreterPath = remoteToolchainPath
    }

    constructor(remoteToolchainPath: String) : this(remoteToolchainPath, RemoteSdkPropertiesHolder(RUST_HELPERS))

    override fun connectionCredentials(): RemoteConnectionCredentialsWrapper {
        return remoteConnectionCredentialsWrapper
    }

    override fun <C> setCredentials(key: Key<C>, credentials: C) {
        remoteConnectionCredentialsWrapper.setCredentials(key, credentials)
    }

    override fun getRemoteConnectionType(): CredentialsType<*> {
        return remoteConnectionCredentialsWrapper.remoteConnectionType
    }

    override fun switchOnConnectionType(vararg cases: CredentialsCase<*>) {
        remoteConnectionCredentialsWrapper.switchType(*cases)
    }

    override fun setSdkId(sdkId: String?) {
        throw IllegalStateException("sdkId in this class is constructed based on fields, so it can't be set")
    }

    override fun getSdkId(): String = constructSdkId(remoteConnectionCredentialsWrapper, remoteSdkProperties)

    override fun getRemoteSdkCredentials(): RsRemoteSdkCredentials? = throw NotImplementedError()

    override fun getRemoteSdkCredentials(
        allowSynchronousInteraction: Boolean
    ): RsRemoteSdkCredentials? = throw NotImplementedError()

    override fun getRemoteSdkCredentials(
        project: Project?,
        allowSynchronousInteraction: Boolean
    ): RsRemoteSdkCredentials? = throw NotImplementedError()

    override fun produceRemoteSdkCredentials(
        remoteSdkCredentialsConsumer: Consumer<RsRemoteSdkCredentials>
    ) = throw NotImplementedError()

    override fun produceRemoteSdkCredentials(
        allowSynchronousInteraction: Boolean,
        remoteSdkCredentialsConsumer: Consumer<RsRemoteSdkCredentials>
    ) = throw NotImplementedError()

    override fun produceRemoteSdkCredentials(
        project: Project?,
        allowSynchronousInteraction: Boolean,
        remoteSdkCredentialsConsumer: Consumer<RsRemoteSdkCredentials>
    ) = throw NotImplementedError()

    override fun getRemoteSdkDataKey(): Any = remoteConnectionCredentialsWrapper.connectionKey

    override fun save(rootElement: Element) {
        super.save(rootElement)
        remoteSdkProperties.save(rootElement)
        remoteConnectionCredentialsWrapper.save(rootElement)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        if (!super.equals(other)) return false

        other as RsRemoteSdkAdditionalData

        if (remoteSdkProperties != other.remoteSdkProperties) return false
        if (remoteConnectionCredentialsWrapper != other.remoteConnectionCredentialsWrapper) return false

        return true
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + remoteSdkProperties.hashCode()
        result = 31 * result + remoteConnectionCredentialsWrapper.hashCode()
        return result
    }

    companion object {
        private const val RUST_HELPERS: String = ".rust_helpers"

        fun load(sdk: Sdk, element: Element?): RsRemoteSdkAdditionalData {
            val path = sdk.homePath
            val remotePath = RemoteSdkCredentialsHolder.getInterpreterPathFromFullPath(path) // TODO
            val data = RsRemoteSdkAdditionalData(remotePath)
            data.load(element)

            if (element != null) {
                CredentialsManager.getInstance().loadCredentials(path, element, data)
                data.remoteSdkProperties.load(element)
            }

            return data
        }

        private fun constructSdkId(
            remoteConnectionCredentialsWrapper: RemoteConnectionCredentialsWrapper,
            properties: RemoteSdkPropertiesHolder
        ): String = remoteConnectionCredentialsWrapper.id + properties.interpreterPath
    }
}
