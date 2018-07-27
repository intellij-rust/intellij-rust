/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.fileTree

class RsRustStructureModificationTrackerToolchainTest : RustWithToolchainTestBase() {
    fun `test mod count incremented on project refresh`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() { }
                """)
            }
        }.create()

        val modTracker = project.rustStructureModificationTracker
        val oldCount = modTracker.modificationCount
        project.cargoProjects.refreshAllProjects()
        check(modTracker.modificationCount > oldCount)
    }
}
