/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.fileTree
import org.rust.lang.core.psi.RsPath

class CargoTomlWatcherIntegrationTest : RustWithToolchainTestBase() {
    fun `test Cargo toml is refreshed`() {
        val p = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []

                [dependencies]
                #foo = { path = "./foo" }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate foo;

                    fn main() {
                        foo::hello();
                    }       //^
                """)
            }


            dir("foo") {
                toml("Cargo.toml", """
                    [package]
                    name = "foo"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                        pub fn hello() {}
                    """)
                }
            }
        }.create()

        p.checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)
        project.cargoProjects.discoverAndRefreshSync()

        val toml = p.root.findFileByRelativePath("Cargo.toml")!!
        runWriteAction {
            VfsUtil.saveText(toml, VfsUtil.loadText(toml).replace("#", ""))
        }


        for (retries in 0..1000) {
            Thread.sleep(10)
            dispatchAllInvocationEvents()
            if (p.findElementInFile<RsPath>("src/main.rs").reference.resolve() != null) {
                return
            }
        }

        error("Failed to resolve the reference")
    }
}
