/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.util.net.HttpConfigurable
import org.rust.lang.RsTestBase
import java.nio.file.Paths

class CargoTest : RsTestBase() {
    fun `test basic command`() = checkCommandLine(
        cargo.toGeneralCommandLine(CargoCommandLine("test", listOf("--all"))),
        """
        cmd: /usr/bin/cargo test --all -- --nocapture
        env: TERM=ansi, RUST_BACKTRACE=full, RUSTC=/usr/bin/rustc
        """
    )

    fun `test propagates proxy settings`() {
        val http = HttpConfigurable().apply {
            USE_HTTP_PROXY = true
            PROXY_AUTHENTICATION = true
            PROXY_HOST = "host"
            PROXY_PORT = 3268
            proxyLogin = "user"
            plainProxyPassword = "pwd"
        }
        val cargo = cargo.apply { setHttp(http) }
        checkCommandLine(
            cargo.toGeneralCommandLine(CargoCommandLine("check")),
            """
            cmd: /usr/bin/cargo check
            env: TERM=ansi, http_proxy=http://user:pwd@host:3268/, RUST_BACKTRACE=full, RUSTC=/usr/bin/rustc
            """
        )
    }

    private fun checkCommandLine(cmd: GeneralCommandLine, expected: String) {
        val cleaned = expected.trimIndent()
        val actual = cmd.debug().trim()
        check(cleaned == actual) {
            "Expected:\n$cleaned\nActual:\n$actual"
        }
    }

    private fun GeneralCommandLine.debug(): String {
        return buildString {
            append("cmd: $commandLineString")
            append("\n")
            append("env: ${environment.entries.joinToString { (key, value) -> "$key=$value" }}")
        }.replace("\\", "/")
    }

    private val toolchain get() = RustToolchain(Paths.get("/usr/bin").toString())
    private val cargo = toolchain.cargo(Paths.get("/my-crate"))
}
