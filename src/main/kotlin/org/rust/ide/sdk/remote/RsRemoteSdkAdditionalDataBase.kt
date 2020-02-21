package org.rust.ide.sdk.remote

import com.intellij.remote.RemoteSdkAdditionalData
import org.rust.ide.sdk.flavors.RsSdkFlavor

interface RsRemoteSdkAdditionalDataBase : RemoteSdkAdditionalData<RsRemoteSdkCredentials> {
    var versionString: String
    val flavor: RsSdkFlavor
}
