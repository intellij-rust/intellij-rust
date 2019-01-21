/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.intellij.util.net.HttpConfigurable

class CargoTest : ToolCommandLineTestBase() {

    private val cargo: Cargo get() = toolchain.rawCargo()

    fun `test run arguments preserved`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", wd, listOf("--bin", "parity", "--", "--prune", "archive"))), """
        cmd: /usr/bin/cargo run --color=always --bin parity -- --prune archive
        env: RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe run --bin parity -- --prune archive
        env: RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test basic command`() = checkCommandLine(
        cargo.toGeneralCommandLine(CargoCommandLine("test", wd, listOf("--all"))), """
        cmd: /usr/bin/cargo test --all
        env: RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe test --all
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
            cargo.toGeneralCommandLine(CargoCommandLine("check", wd)), """
            cmd: /usr/bin/cargo check
            env: RUST_BACKTRACE=short, TERM=ansi, http_proxy=http://user:pwd@host:3268/
            """, """
            cmd: C:/usr/bin/cargo.exe check
            env: RUST_BACKTRACE=short, TERM=ansi, http_proxy=http://user:pwd@host:3268/
        """)
    }

    fun `test adds colors for common commands`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", wd, listOf("--release", "--", "foo"))), """
        cmd: /usr/bin/cargo run --color=always --release -- foo
        env: RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe run --release -- foo
        env: RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test don't add color for unknown command`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("tree", wd)), """
        cmd: /usr/bin/cargo tree
        env: RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe tree
        env: RUST_BACKTRACE=short, TERM=ansi
    """)

    fun `test adds nightly channel`() = checkCommandLine(
        cargo.toColoredCommandLine(CargoCommandLine("run", wd, listOf("--release", "--", "foo"), channel = RustChannel.NIGHTLY)), """
        cmd: /usr/bin/cargo +nightly run --color=always --release -- foo
        env: RUST_BACKTRACE=short, TERM=ansi
        """, """
        cmd: C:/usr/bin/cargo.exe +nightly run --release -- foo
        env: RUST_BACKTRACE=short, TERM=ansi
    """)


}
