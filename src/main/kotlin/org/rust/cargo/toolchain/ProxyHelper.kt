/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.net.HttpConfigurable
import java.net.URI

fun withProxyIfNeeded(cmdLine: GeneralCommandLine, http: HttpConfigurable) {
    if (http.USE_HTTP_PROXY && http.PROXY_HOST.isNotEmpty()) {
        cmdLine.withEnvironment("http_proxy", http.proxyUri.toString())
    }
}

private val HttpConfigurable.proxyUri: URI
    get() {
        var userInfo: String? = null
        if (PROXY_AUTHENTICATION && !proxyLogin.isNullOrEmpty() && plainProxyPassword != null) {
            val login = proxyLogin
            val password = plainProxyPassword!!
            userInfo = if (password.isNotEmpty()) login + ":" + password else login
        }
        return URI("http", userInfo, PROXY_HOST, PROXY_PORT, "/", null, null)
    }
