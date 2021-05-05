/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects

class CargoWithToolchainCommandCompletionProviderTest : RsWithToolchainTestBase() {
    fun `test target triple completion includes popular targets`() {
        val testProject = buildProject {
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

        val cargoProjects = testProject.psiFile("src/main.rs").project.cargoProjects
        val cargoProject = cargoProjects.allProjects.first()
        val completions = CargoCommandCompletionProviderTest.complete("build --target ", cargoProjects, cargoProject.workspace)

        assertContainsElements(
            completions,
            // Tier 1
            "aarch64-unknown-linux-gnu",
            "i686-pc-windows-gnu",
            "i686-pc-windows-msvc",
            "i686-pc-windows-msvc",
            "x86_64-apple-darwin",
            "x86_64-pc-windows-gnu",
            "x86_64-pc-windows-msvc",
            "x86_64-pc-windows-gnu",
            // Other popular
            "wasm32-unknown-unknown",
            "wasm32-wasi"
        )
    }
}
