/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.fileTree
import org.rust.ide.actions.ui.CargoNewCrateSettings
import org.rust.ide.actions.ui.CargoNewCrateUI
import org.rust.ide.actions.ui.withMockCargoNewCrateUi
import org.rust.launchAction

class RsCreateCrateActionTest : RsWithToolchainTestBase() {
    fun `test create with file context`() {
        createProjectScaffold()

        val main = cargoProjectDirectory.findFileByRelativePath("Cargo.toml") ?: error("Main file was not found")
        createCrate(main, "crate1")
    }

    fun `test create binary crate`() {
        createProjectScaffold()
        createCrate(cargoProjectDirectory, "crate1")
    }

    fun `test create library crate`() {
        createProjectScaffold()
        createCrate(cargoProjectDirectory, "crate1", binary = false)
    }

    private fun createCrate(file: VirtualFile, name: String, binary: Boolean = true) {
        withMockCargoNewCrateUi(object : CargoNewCrateUI {
            override fun selectCargoCrateSettings(): CargoNewCrateSettings {
                return CargoNewCrateSettings(binary, name)
            }
        }) {
            myFixture.launchAction("Rust.NewCargoCrate", CommonDataKeys.VIRTUAL_FILE to file)
        }

        val src = if (binary) "main" else "lib"

        val main = cargoProjectDirectory.findFileByRelativePath("$name/src/$src.rs")
            ?: error("Source file was not found for crate $name")
        val pkg = project.cargoProjects.findPackageForFile(main) ?: error("Package was not found for file $main")
        assertEquals(1, pkg.targets.size)
    }

    private fun createProjectScaffold() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {
                        println!("Hello, world!");
                    }
                """)
            }
        }.create()
    }
}
