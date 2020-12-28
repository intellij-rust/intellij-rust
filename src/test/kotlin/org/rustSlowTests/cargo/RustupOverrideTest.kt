/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo

import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.toolchain.RustChannel
import org.rust.singleProject

class RustupOverrideTest : RsWithToolchainTestBase() {

    fun test() {
        buildProject {
            toml("rust-toolchain", """
                [toolchain]
                channel = "nightly"
            """)
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("main.rs", """
                    fn main() {}
                """)
            }
        }
        assertEquals(RustChannel.NIGHTLY, project.cargoProjects.singleProject().rustcInfo?.version?.channel)
    }
}
