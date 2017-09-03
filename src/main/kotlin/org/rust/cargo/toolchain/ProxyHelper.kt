/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.net.HttpConfigurable
import java.net.URI

class ProxyHelper {

    private var httpSettings: HttpConfigurable = HttpConfigurable.getInstance();

    private val proxyUri: URI get() {
        var userInfo: String? = null
        if (httpSettings.PROXY_AUTHENTICATION
            && httpSettings.proxyLogin != null
            && httpSettings.proxyLogin.toString().isNotEmpty()
            && httpSettings.plainProxyPassword != null
            ) {
            val login = httpSettings.proxyLogin
            val password = httpSettings.plainProxyPassword!!;
            userInfo = if (password.isNotEmpty()) login + ":" + password else login;
        }
        return URI("http", userInfo, httpSettings.PROXY_HOST, httpSettings.PROXY_PORT, "/", null, null);
    }

    fun withProxyIfNeeded(cmdLine: GeneralCommandLine) {
        if (httpSettings.USE_HTTP_PROXY && httpSettings.PROXY_HOST.isNotEmpty()) {
            cmdLine.withEnvironment("http_proxy", proxyUri.toString())
        }
    }
}
