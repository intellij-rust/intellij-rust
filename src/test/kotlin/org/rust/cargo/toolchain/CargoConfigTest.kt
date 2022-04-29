/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import org.rust.MinRustcVersion
import org.rust.cargo.CargoConfig
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.fileTree
import org.rust.singleWorkspace

@MinRustcVersion("1.53.0")
class CargoConfigTest : RsWithToolchainTestBase() {
    fun `test build target`() {
        fileTree {
            dir(".cargo") {
                file("config.toml", """
                        [build]
                        target = "wasm32-unknown-unknown"
                    """)
            }
            file("Cargo.toml", CARGO_TOML)
            dir("src") { file("main.rs") }
        }.create()

        val buildTarget = project.cargoProjects.singleWorkspace().cargoConfig.buildTarget

        assertEquals("wasm32-unknown-unknown", buildTarget)
    }

    fun `test env`() {
        fileTree {
            dir(".cargo") {
                file("config.toml", """
                        [env]
                        foo = "42"
                        bar = { value = "24", forced = true }
                        baz = { value = "hello/world", relative = true }
                    """)
            }
            file("Cargo.toml", CARGO_TOML)
            dir("src") { file("main.rs") }
        }.create()

        val env = project.cargoProjects.singleWorkspace().cargoConfig.env

        assertEquals(CargoConfig.EnvValue("42"), env["foo"])
        assertEquals(CargoConfig.EnvValue("24", isForced = true), env["bar"])
        assertEquals(CargoConfig.EnvValue("hello/world", isRelative = true), env["baz"])
    }

    companion object {
        private val CARGO_TOML = """
            [package]
            name = "foo"
            version = "0.1.0"
        """.trimIndent()
    }
}
