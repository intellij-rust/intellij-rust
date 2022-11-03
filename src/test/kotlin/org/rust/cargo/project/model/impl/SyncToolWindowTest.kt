/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BuildViewTestFixture
import org.rust.*
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import java.nio.file.Path

@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS)
class SyncToolWindowTest : RsWithToolchainTestBase() {

    private lateinit var buildViewTestFixture: BuildViewTestFixture

    override fun setUp() {
        super.setUp()
        buildViewTestFixture = BuildViewTestFixture(project)
        buildViewTestFixture.setUp()
    }

    override fun tearDown() {
        buildViewTestFixture.tearDown()
        super.tearDown()
    }

    fun `test non-existing project`() {
        project.cargoProjects.attachCargoProject(Path.of("/non-existing-project/Cargo.toml"))
        checkSyncViewTree("""
            -
             -failed
              -Sync non-existing-project project
               Project directory does not exist
        """)
    }

    fun `test single project`() {
        val project = buildProject {
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
        checkSyncViewTree("""
            -
             -finished
              -Sync ${project.root.name} project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Checking hello v0.1.0
               Getting Rust stdlib
        """)
    }

    fun `test several cargo projects 1`() {
        val project = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "crate1"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {}
                """)
            }
            dir("crate2") {
                toml("Cargo.toml", """
                    [package]
                    name = "crate2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                }
            }
        }

        attachCargoProject(project.file("crate2"))

        checkSyncViewTree("""
            -
             -finished
              -Sync crate1 project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Checking crate1 v0.1.0
               Getting Rust stdlib
              -Sync crate2 project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Checking crate2 v0.1.0
               Getting Rust stdlib
        """)
    }

    fun `test several cargo projects 2`() {
        val project = buildProject {
            dir("crate1") {
                toml("Cargo.toml", """
                    [package]
                    name = "crate1"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                }
            }
            dir("crate2") {
                toml("Cargo.toml", """
                    [package]
                    name = "crate2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                }
            }
        }
        attachCargoProject(project.file("crate1"))
        attachCargoProject(project.file("crate2"))

        checkSyncViewTree("""
            -
             -finished
              -Sync crate1 project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Checking crate1 v0.1.0
               Getting Rust stdlib
              -Sync crate2 project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Checking crate2 v0.1.0
               Getting Rust stdlib
        """)
    }

    fun `test with error in manifest`() {
        val project = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "hello
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {}
                """)
            }
        }
        checkSyncViewTree("""
            -
             -failed
              -Sync ${project.root.name} project
               Getting toolchain version
               -Updating workspace info
                Failed to run Cargo
               Getting Rust stdlib
        """)
    }

    fun `test no projects`() {
        val testProject = fileTree {
            dir("crate") {
                toml("Cargo.toml", """
                    [package]
                    name = "crate"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                }
            }
        }.create(project, cargoProjectDirectory)
        val crateRoot = testProject.file("crate")
        attachCargoProject(crateRoot)
        val cargoProject = project.testCargoProjects.refreshAllProjectsSync().single()
        detachCargoProject(cargoProject)

        // This refresh shouldn't change Sync view since there isn't any Cargo project
        project.testCargoProjects.refreshAllProjectsSync()

        checkSyncViewTree("""
            -
             -finished
              -Sync crate project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Checking crate v0.1.0
               Getting Rust stdlib
        """)
    }

    fun `test with build script evaluation`() {
        val project = buildProject {
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
            rust("build.rs", """
                fn main() {}
            """)
        }
        checkSyncViewTree("""
            -
             -finished
              -Sync ${project.root.name} project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Compiling hello v0.1.0
               Getting Rust stdlib
        """)
    }

    fun `test with compile error in build script`() {
        val project = buildProject {
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
            rust("build.rs", """
                fn main() {
                    let a: i32 = "";
                }
            """)
        }
        checkSyncViewTree("""
            -
             -finished
              -Sync ${project.root.name} project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 -Compiling hello v0.1.0
                  -build.rs
                   Mismatched types
                Build scripts evaluation failed
               Getting Rust stdlib
        """)
    }

    fun `test with panic in build script`() {
        val project = buildProject {
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
            rust("build.rs", """
                fn main() {
                    panic!("I'm panic!");
                }
            """)
        }
        checkSyncViewTree("""
            -
             -finished
              -Sync ${project.root.name} project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Compiling hello v0.1.0
                 -${project.root.name}
                  Failed to run custom build command for `hello v0.1.0 (${FileUtil.toSystemDependentName(project.root.path)})`
                Build scripts evaluation failed
               Getting Rust stdlib
        """)
    }

    @MinRustcVersion("1.62.0")
    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS)
    fun `test Cargo continues build script evaluation after a compilation error in an independent crate`() {
        if (SystemInfo.isWindows) return // It looks like error messages are a bit different on Windows
                                         // TODO test it on windows using different expected output
        val project = buildProject {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "with-build-script",
                    "with-proc-macro"
                ]
            """)

            dir("with-build-script") {
                toml("Cargo.toml", """
                    [package]
                    name = "with-build-script"
                    version = "0.1.0"
                """)

                dir("src") {
                    rust("lib.rs", """
                        fn main() {}
                    """)
                }
                rust("build.rs", """
                    fn main() { panic!("Foo!"); }
                """)
            }

            dir("with-proc-macro") {
                toml("Cargo.toml", """
                    [package]
                    name = "with-proc-macro"
                    version = "0.1.0"

                    [lib]
                    proc-macro = true
                    path = "src/lib.rs"

                    [[bin]]
                    path = "src/main.rs"
                    name = "with-proc-macro-bin"
                """)

                dir("src") {
                    rust("main.rs", """
                        fn main() {}
                    """)
                    rust("lib.rs", """
                        fn foobar() { bar } // Compile error
                    """)
                }
            }
        }
        checkSyncViewTree("""
            -
             -finished
              -Sync ${project.root.name} project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Compiling with-build-script v0.1.0
                 -Compiling with-proc-macro v0.1.0
                  -lib.rs
                   Cannot find value `bar` in this scope
                 -${project.root.name}
                  Failed to run custom build command for `with-build-script v0.1.0 (${FileUtil.toSystemDependentName(project.root.path)}/with-build-script)`
                Build scripts evaluation failed
               Getting Rust stdlib
        """)
    }

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS)
    fun `test build script evaluation without build script wrapper`() {
        setRegistryOptionEnabled(Cargo.USE_BUILD_SCRIPT_WRAPPER, false, testRootDisposable)

        val project = buildProject {
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
        checkSyncViewTree("""
            -
             -finished
              -Sync ${project.root.name} project
               Getting toolchain version
               -Updating workspace info
                -Build scripts evaluation
                 Checking hello v0.1.0
               Getting Rust stdlib
        """)
    }


    private fun attachCargoProject(cargoProjectRoot: VirtualFile) {
        myFixture.launchAction("Cargo.AttachCargoProject", PlatformDataKeys.VIRTUAL_FILE to cargoProjectRoot)
    }

    private fun detachCargoProject(cargoProject: CargoProject) {
        myFixture.launchAction("Cargo.DetachCargoProject", CargoToolWindow.SELECTED_CARGO_PROJECT to cargoProject)
    }

    private fun checkSyncViewTree(expected: String) {
        project.testCargoProjects.discoverAndRefreshSync()
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        buildViewTestFixture.assertSyncViewTreeEquals(expected.trimIndent())
    }
}
