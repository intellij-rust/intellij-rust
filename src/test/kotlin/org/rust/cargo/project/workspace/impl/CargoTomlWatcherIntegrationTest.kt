package org.rust.cargo.project.workspace.impl

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspaceService
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.fileTree
import org.rust.lang.core.psi.RsPath

class CargoTomlWatcherIntegrationTest : RustWithToolchainTestBase() {
    override val dataPath: String = ""

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
        }.create(project, project.baseDir)

        CargoProjectWorkspaceService.getInstance(module).syncUpdate(module.project.toolchain!!)
        if (module.cargoWorkspace == null) {
            error("Failed to update a test Cargo project")
        }
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
