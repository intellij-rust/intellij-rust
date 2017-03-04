package org.rust.cargo.toolchain

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import org.rust.TestProject
import org.rust.TestProjectBuilder
import org.rust.cargo.RustWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspaceService
import org.rust.cargo.project.workspace.SetupRustStdlibTask
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.lang.core.psi.RsPath
import org.rust.utils.fullyRefreshDirectory

class CargoProjectResolveTest : RustWithToolchainTestBase() {

    override val dataPath: String = ""

    fun `test resolve external library`() = buildProject {
        toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                rand = "=0.3.14"
        """)

        rust("src/main.rs", """
                extern crate rand;

                use rand::distributions;

                mod foo;

                fn main() {
                    let _ = distributions::normal::Normal::new(0.0, 1.0);
                }                         //^
        """)
    }.checkReferenceIsResolved<RsPath>("src/main.rs")

    fun `test resolve local package`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [dependencies]
            foo = { path = "./foo" }
        """)

        rust("src/main.rs", """
            extern crate foo;
            mod bar;

            fn main() {
                foo::hello();
            }       //^
        """)

        rust("src/bar.rs", """
            use foo::hello;

            pub fn bar() {
                hello();
            }   //^
        """)

        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)

            rust("src/lib.rs", """
                pub fn hello() {}
            """)
        }
    }.run {
        checkReferenceIsResolved<RsPath>("src/main.rs")
        checkReferenceIsResolved<RsPath>("src/bar.rs")
    }

    fun `test module relations`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "mods"
            version = "0.1.0"
            authors = []

            [dependencies]
        """)

        rust("src/lib.rs", """
            mod foo;

            pub struct S;
        """)

        rust("src/foo.rs", """
            use S;
              //^
        """)
    }.checkReferenceIsResolved<RsPath>("src/foo.rs")

    fun `test kebab-case`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "kebab-case"
            version = "0.1.0"
            authors = []

            [dependencies]
        """)

        rust("src/main.rs", """
            extern crate kebab_case;

            fn main() {
                kebab_case::foo();
            }              //^
        """)

        rust("src/lib.rs", "pub fn foo() { }")

    }.checkReferenceIsResolved<RsPath>("src/main.rs")

    fun `test case insensitive mods`() {
        if (!SystemInfo.isWindows) return
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "mods"
                version = "0.1.0"
                authors = []

                [dependencies]
            """)

            rust("src/lib.rs", "mod foo; mod bar;")
            rust("src/FOO.rs", "pub struct Spam;")

            rust("src/BAR.rs", """
                use foo::Spam;
                         //^
            """)
        }.checkReferenceIsResolved<RsPath>("src/BAR.rs")
    }

    // Test that we don't choke on winapi crate, which uses **A LOT** of
    // glob imports and is just **ENORMOUS**
    fun `test winapi torture`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [dependencies]
            winapi = "0.2"
        """)

        rust("src/main.rs", """
            extern crate winapi;
            use winapi::*;

            fn main() {
                let _ = foo;
            }          //^
        """)
    }.checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)

    fun buildProject(builder: TestProjectBuilder.() -> Unit): TestProject =
        TestProjectBuilder(project).build(builder).apply {
            refreshWorkspace()
        }

    private fun refreshWorkspace() {
        CargoProjectWorkspaceService.getInstance(module).syncUpdate(module.project.toolchain!!)
        if (module.cargoWorkspace == null) {
            error("Failed to update a test Cargo project")
        }
    }


    @Suppress("unused")
    private fun openRealProject(path: String) {
        runWriteAction {
            VfsUtil.copyDirectory(
                this,
                LocalFileSystem.getInstance().findFileByPath(path)!!,
                project.baseDir,
                { true }
            )
            fullyRefreshDirectory(project.baseDir)
        }

        refreshWorkspace()
        SetupRustStdlibTask(module, project.toolchain!!.rustup(project.baseDir.path)!!).apply {
            run(EmptyProgressIndicator())
            onSuccess()
        }
    }
}
