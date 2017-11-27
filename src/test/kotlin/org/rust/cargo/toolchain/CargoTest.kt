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

    fun `test run arguments preserved`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", wd, listOf("--bin", "parity", "--", "--prune", "archive"))), """
        cmd: /usr/bin/cargo run --color=always --bin parity -- --prune archive
        env: RUST_BACKTRACE=full, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe run --bin parity -- --prune archive
        env: RUST_BACKTRACE=full, TERM=ansi
    """)

    fun `test basic command`() = checkCommandLine(
        cargo.toGeneralCommandLine(CargoCommandLine("test", wd, listOf("--all"))), """
        cmd: /usr/bin/cargo test --all -- --nocapture
        env: RUST_BACKTRACE=full, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe test --all -- --nocapture
        env: RUST_BACKTRACE=full, TERM=ansi
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
            cargo.toGeneralCommandLine(CargoCommandLine("check", wd)), """
            cmd: /usr/bin/cargo check
            env: RUST_BACKTRACE=full, TERM=ansi, http_proxy=http://user:pwd@host:3268/
            """, """
            cmd: C:/usr/bin/cargo.exe check
            env: RUST_BACKTRACE=full, TERM=ansi, http_proxy=http://user:pwd@host:3268/
        """)
    }

    fun `test adds colors for common commands`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", wd, listOf("--release", "--", "foo"))), """
        cmd: /usr/bin/cargo run --color=always --release -- foo
        env: RUST_BACKTRACE=full, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe run --release -- foo
        env: RUST_BACKTRACE=full, TERM=ansi
    """)

    fun `test don't add color for unknown command`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("tree", wd)), """
        cmd: /usr/bin/cargo tree
        env: RUST_BACKTRACE=full, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe tree
        env: RUST_BACKTRACE=full, TERM=ansi
    """)

    fun `test adds nightly channel`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", wd, listOf("--release", "--", "foo"), channel = RustChannel.NIGHTLY)), """
        cmd: /usr/bin/cargo +nightly run --color=always --release -- foo
        env: RUST_BACKTRACE=full, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe +nightly run --release -- foo
        env: RUST_BACKTRACE=full, TERM=ansi
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

        var result = buildString {
            append("cmd: $commandLineString")
            append("\n")
            append("env: ${env.joinToString { (key, value) -> "$key=$value" }}")
        }

        if (SystemInfo.isWindows) {
            result = result.toUnixSlashes().replace(drive, "C:/")
        }

        return result
    }

    private val toolchain get() = RustToolchain(Paths.get("/usr/bin"))
    private val cargo = toolchain.rawCargo()
    private val drive = Paths.get("/").toAbsolutePath().toString().toUnixSlashes()
    private val wd = Paths.get("/my-crate")

    private fun String.toUnixSlashes(): String = replace("\\", "/")
}
