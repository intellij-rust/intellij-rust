/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.net.HttpConfigurable
import org.rust.lang.RsTestBase
import java.nio.file.Paths

class CargoTest : RsTestBase() {
    fun `test basic command`() = checkCommandLine(
        cargo.toGeneralCommandLine(CargoCommandLine("test", listOf("--all"))), """
        cmd: /usr/bin/cargo test --all -- --nocapture
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=full, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe test --all -- --nocapture
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=full, TERM=ansi
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
        val cargo = cargo.apply { setHttp(http) }
        checkCommandLine(
            cargo.toGeneralCommandLine(CargoCommandLine("check")), """
            cmd: /usr/bin/cargo check
            env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=full, TERM=ansi, http_proxy=http://user:pwd@host:3268/
            """, """
            cmd: C:/usr/bin/cargo.exe check
            env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=full, TERM=ansi, http_proxy=http://user:pwd@host:3268/
        """)
    }

    fun `test adds colors for common commands`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", listOf("--release", "--", "foo"))), """
        cmd: /usr/bin/cargo run --color=always --release -- foo
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=full, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe run --release -- foo
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=full, TERM=ansi
    """)

    fun `test don't add color for unknown command`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("tree")), """
        cmd: /usr/bin/cargo tree
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=full, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe tree
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=full, TERM=ansi
    """)

    private fun checkCommandLine(cmd: GeneralCommandLine, expected: String, expectedWin: String) {
        val cleaned = (if (SystemInfo.isWindows) expectedWin else expected).trimIndent()
        val actual = cmd.debug().trim()
        check(cleaned == actual) {
            "Expected:\n$cleaned\nActual:\n$actual"
        }
    }

    private fun GeneralCommandLine.debug(): String {
        val env = environment.entries.sortedBy { it.key }
        return buildString {
            append("cmd: $commandLineString")
            append("\n")
            append("env: ${env.joinToString { (key, value) -> "$key=$value" }}")
        }.replace("\\", "/")
    }

    private val toolchain get() = RustToolchain(Paths.get("/usr/bin").toString())
    private val cargo = toolchain.cargo(Paths.get("/my-crate"))
}
