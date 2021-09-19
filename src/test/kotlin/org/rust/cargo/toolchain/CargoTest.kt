/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.net.HttpConfigurable
import org.rust.MockRustcVersion
import org.rust.RsTestBase
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.tools.cargo
import java.nio.file.Paths

class CargoTest : RsTestBase() {

    fun `test run arguments preserved`() = checkCommandLine(
        cargo.toColoredCommandLine(project, CargoCommandLine("run", wd, listOf("--bin", "parity", "--", "--prune", "archive"))), """
        cmd: /usr/bin/cargo run --color=always --bin parity -- --prune archive
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe run --color=always --bin parity -- --prune archive
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test basic command`() = checkCommandLine(
        cargo.toGeneralCommandLine(project, CargoCommandLine("test", wd, listOf("--all"))), """
        cmd: /usr/bin/cargo test --all
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe test --all
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi
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
            cargo.toGeneralCommandLine(project, CargoCommandLine("check", wd)), """
            cmd: /usr/bin/cargo check
            env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi, http_proxy=http://user:pwd@host:3268/
            """, """
            cmd: C:/usr/bin/cargo.exe check
            env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi, http_proxy=http://user:pwd@host:3268/
        """)
    }

    fun `test adds colors for common commands`() = checkCommandLine(
        cargo.toColoredCommandLine(project, CargoCommandLine("run", wd, listOf("--release", "--", "foo"))), """
        cmd: /usr/bin/cargo run --color=always --release -- foo
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe run --color=always --release -- foo
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test don't add color for unknown command`() = checkCommandLine(
        cargo.toColoredCommandLine(project, CargoCommandLine("tree", wd)), """
        cmd: /usr/bin/cargo tree
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe tree
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test adds nightly channel`() = checkCommandLine(
        cargo.toColoredCommandLine(project, CargoCommandLine("run", wd, listOf("--release", "--", "foo"), channel = RustChannel.NIGHTLY)), """
        cmd: /usr/bin/cargo +nightly run --color=always --release -- foo
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe +nightly run --color=always --release -- foo
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test use +nightly explicitly`() = checkCommandLine(
        cargo.toColoredCommandLine(project, CargoCommandLine("run", wd, toolchain = "+nightly")), """
        cmd: /usr/bin/cargo +nightly run --color=always
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe +nightly run --color=always
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test override explicitly used +toolchain flag if channel is set`() = checkCommandLine(
        cargo.toColoredCommandLine(project, CargoCommandLine("run", wd, toolchain = "+nightly", channel = RustChannel.BETA)), """
        cmd: /usr/bin/cargo +beta run --color=always
        env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe +beta run --color=always
        env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi
    """)

    @MockRustcVersion("1.36.0")
    fun `test adds --offline option`() = withOfflineMode {
        checkCommandLine(cargo.toColoredCommandLine(project, CargoCommandLine("run", wd, listOf("--release", "--", "foo"))), """
            cmd: /usr/bin/cargo --offline run --color=always --release -- foo
            env: RUSTC=/usr/bin/rustc, RUST_BACKTRACE=short, TERM=ansi
            """, """
            cmd: C:/usr/bin/cargo.exe --offline run --color=always --release -- foo
            env: RUSTC=C:/usr/bin/rustc.exe, RUST_BACKTRACE=short, TERM=ansi
        """)
    }

    private fun withOfflineMode(action: () -> Unit) {
        val oldStatus = project.rustSettings.useOffline
        try {
            project.rustSettings.modify { it.useOffline = true }
            action()
        } finally {
            project.rustSettings.modify { it.useOffline = oldStatus }
        }
    }

    private fun checkCommandLine(
        cmd: GeneralCommandLine,
        expected: String,
        expectedWin: String
    ) {
        val cleaned = (if (SystemInfo.isWindows) expectedWin else expected).trimIndent()
        val actual = cmd.debug().trim()
        assertEquals(cleaned, actual)
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

    private val toolchain get() = RsLocalToolchain(Paths.get("/usr/bin"))
    private val cargo = toolchain.cargo()
    private val drive = Paths.get("/").toAbsolutePath().toString().toUnixSlashes()
    private val wd = Paths.get("/my-crate")

    private fun String.toUnixSlashes(): String = replace("\\", "/")
}
