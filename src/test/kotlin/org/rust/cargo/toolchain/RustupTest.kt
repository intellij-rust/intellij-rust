/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.util.net.HttpConfigurable

class RustupTest : ToolCommandLineTestBase() {

    private val rustup: Rustup get() = toolchain.rustup(wd, false) ?: error("Failed to get `rustup`")

    fun `test base command`() = checkCommandLine(rustup.addComponentCommand("rust-src"), """
        cmd: /usr/bin/rustup component add rust-src
        env:
    """, """
        cmd: C:/usr/bin/rustup.exe component add rust-src
        env:
    """)

    fun `test propagates proxy settings`() {
        val http = HttpConfigurable().apply {
            USE_HTTP_PROXY = true
            PROXY_AUTHENTICATION = true
            PROXY_HOST = "host"
            PROXY_PORT = 3268
            proxyLogin = "user"
            plainProxyPassword = "pwd"
        }
        val rustup = rustup.apply { setHttp(http) }
        checkCommandLine(rustup.addComponentCommand("rust-src"), """
            cmd: /usr/bin/rustup component add rust-src
            env: http_proxy=http://user:pwd@host:3268/
        """, """
            cmd: C:/usr/bin/rustup.exe component add rust-src
            env: http_proxy=http://user:pwd@host:3268/
        """)
    }
}
