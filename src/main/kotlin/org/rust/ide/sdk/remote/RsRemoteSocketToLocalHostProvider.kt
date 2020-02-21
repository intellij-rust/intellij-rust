package org.rust.ide.sdk.remote

import com.intellij.remote.RemoteSdkException

interface RsRemoteSocketToLocalHostProvider {
    @Throws(RemoteSdkException::class)
    fun getRemoteSocket(localPort: Int): Pair<String, Int>
}
