/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.net.HttpConfigurable
import org.rust.RsTestBase
import java.nio.file.Path
import java.nio.file.Paths

class CargoTest : RsTestBase() {

    fun `test run arguments preserved`() = checkCommandLine(
        cargo.toColoredCommandLine(
            CargoCommandLine("run", workingDir, listOf("--bin", "parity", "--", "--prune", "archive"))
        ), """
        cmd: /usr/bin/cargo run --color=always --bin parity -- --prune archive
        env: RUST_BACKTRACE=short, TERM=ansi
    """, """
        cmd: C:/usr/bin/cargo.exe run --bin parity -- --prune archive
        env: RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test basic command`() = checkCommandLine(
        cargo.toGeneralCommandLine(
            CargoCommandLine("test", workingDir, listOf("--all"))
        ), """
        cmd: /usr/bin/cargo test --all -- --nocapture
        env: RUST_BACKTRACE=short, TERM=ansi
    """, """
        cmd: C:/usr/bin/cargo.exe test --all -- --nocapture
        env: RUST_BACKTRACE=short, TERM=ansi
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
            cargo.toGeneralCommandLine(CargoCommandLine("check", workingDir)), """
            cmd: /usr/bin/cargo check
            env: RUST_BACKTRACE=short, TERM=ansi, http_proxy=http://user:pwd@host:3268/
            """, """
            cmd: C:/usr/bin/cargo.exe check
            env: RUST_BACKTRACE=short, TERM=ansi, http_proxy=http://user:pwd@host:3268/
        """)
    }

    fun `test adds colors for common commands`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", workingDir, listOf("--release", "--", "foo"))), """
        cmd: /usr/bin/cargo run --color=always --release -- foo
        env: RUST_BACKTRACE=short, TERM=ansi
    """, """
        cmd: C:/usr/bin/cargo.exe run --release -- foo
        env: RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test don't add color for unknown command`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("tree", workingDir)), """
        cmd: /usr/bin/cargo tree
        env: RUST_BACKTRACE=short, TERM=ansi
    """, """
        cmd: C:/usr/bin/cargo.exe tree
        env: RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test adds nightly channel`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", workingDir, listOf("--release", "--", "foo"), channel = RustChannel.NIGHTLY)), """
        cmd: /usr/bin/cargo +nightly run --color=always --release -- foo
        env: RUST_BACKTRACE=short, TERM=ansi
    """, """
        cmd: C:/usr/bin/cargo.exe +nightly run --release -- foo
        env: RUST_BACKTRACE=short, TERM=ansi
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

    private val cargo: Cargo = toolchain.rawCargo()

    private val workingDir: Path = Paths.get("/my-crate")

    private val drive: String =
        Paths.get("/").toAbsolutePath().toString().toUnixSlashes()

    private val toolchain: RustToolchain
        get() = RustToolchain(Paths.get("/usr/bin"))

    private fun String.toUnixSlashes(): String =
        replace("\\", "/")
}
