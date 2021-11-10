/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.lang.resolve

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import org.rust.*
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.lang.core.crate.impl.CrateGraphTestmarks
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.resolve.NameResolutionTestmarks
import org.rust.openapiext.pathAsPath

class CargoProjectResolveTest : RsWithToolchainTestBase() {

    private val tempDirFixture = TempDirTestFixtureImpl()

    override fun setUp() {
        super.setUp()
        tempDirFixture.setUp()
    }

    override fun tearDown() {
        tempDirFixture.tearDown()
        super.tearDown()
    }

    fun `test resolve external library which hides std crate`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []

            [dependencies]
            libc = "=0.2.30"
        """)

        dir("src") {
            rust("main.rs", """
                extern crate libc;
                use libc::int8_t;
                          //^
            """)
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs")

    fun `test resolve external library which hides std crate in dependency`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                foo = { path = "./foo" }
            """)

            dir("src") {
                rust("lib.rs", "")
            }

            dir("foo") {
                toml("Cargo.toml", """
                    [package]
                    name = "foo"
                    version = "0.1.0"
                    authors = []

                    [dependencies]
                    libc = "=0.2.30"
                """)

                dir("src") {
                    rust("lib.rs", """
                        extern crate libc;
                        use libc::int8_t;
                                  //^
                    """)
                }
            }
        }
        NameResolutionTestmarks.shadowingStdCrates.ignoreInNewResolve(project).checkHit {
            testProject.checkReferenceIsResolved<RsPath>("foo/src/lib.rs")
        }
    }

    fun `test resolve local package`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [dependencies]
            foo = { path = "./foo" }
        """)

        dir("src") {
            rust("main.rs", """
                extern crate foo;
                mod bar;

                fn main() {
                    foo::hello();
                }       //^
            """)

            rust("bar.rs", """
                use foo::hello;

                pub fn bar() {
                    hello();
                }   //^
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
    }.run {
        checkReferenceIsResolved<RsPath>("src/main.rs")
        checkReferenceIsResolved<RsPath>("src/bar.rs")
    }

    fun `test resolve local package under symlink`() = fileTree {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [dependencies]
            foo = { path = "./foo" }
        """)

        dir("src") {
            rust("main.rs", """
                extern crate foo;
                mod bar;

                fn main() {
                    foo::hello();
                }       //^
            """)

            rust("bar.rs", """
                use foo::hello;

                pub fn bar() {
                    hello();
                }   //^
            """)
        }

        symlink("foo", "foo2")

        dir("foo2") {
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
    }.let { fileTree ->
        // Symlinks doesn't work on Windows CI for some reason
        if (SystemInfo.isWindows) return@let

        val project = fileTree.create()
        project.checkReferenceIsResolved<RsPath>("src/main.rs")
        project.checkReferenceIsResolved<RsPath>("src/bar.rs")
    }

    fun `test module relations`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "mods"
            version = "0.1.0"
            authors = []

            [dependencies]
        """)

        dir("src") {
            rust("lib.rs", """
                mod foo;

                pub struct S;
            """)

            rust("foo.rs", """
                use S;
                  //^
            """)
        }
    }.checkReferenceIsResolved<RsPath>("src/foo.rs")

    fun `test module relations when cargo project is under symlink`() = fileTree {
        symlink("foo", "symlink_target")

        dir("symlink_target") {
            toml("Cargo.toml", """
                [package]
                name = "mods"
                version = "0.1.0"
                authors = []

                [dependencies]
            """)

            dir("src") {
                rust("lib.rs", """
                    mod foo;

                    pub struct S;
                """)

                rust("foo.rs", """
                    use S;
                      //^
                """)
            }
        }
    }.let { fileTree ->
        // Symlinks doesn't work on Windows CI for some reason
        if (SystemInfo.isWindows) return@let

        val p = fileTree.create(project, cargoProjectDirectory)
        project.testCargoProjects.attachCargoProject(cargoProjectDirectory.pathAsPath.resolve("foo/Cargo.toml"))
        excludeDirectoryFromIndex("symlink_target")

        p.checkReferenceIsResolved<RsPath>("foo/src/foo.rs")
    }

    fun `test module relations when parent of cargo project is under symlink`() = fileTree {
        symlink("symlink_source", "symlink_target")

        dir("symlink_target") {
            dir("project") {
                toml("Cargo.toml", """
                [package]
                name = "mods"
                version = "0.1.0"
                authors = []

                [dependencies]
            """)

                dir("src") {
                    rust("lib.rs", """
                        mod foo;

                        pub struct S;
                """)

                    rust("foo.rs", """
                        use S;
                          //^
                """)
                }
            }
        }
    }.let { fileTree ->
        // Symlinks doesn't work on Windows CI for some reason
        if (SystemInfo.isWindows) return@let

        val p = fileTree.create(project, cargoProjectDirectory)
        project.testCargoProjects.attachCargoProject(cargoProjectDirectory.pathAsPath.resolve("symlink_source/project/Cargo.toml"))
        excludeDirectoryFromIndex("symlink_target/project")

        p.checkReferenceIsResolved<RsPath>("symlink_source/project/src/foo.rs")
    }

    fun `test kebab-case`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "kebab-case"
            version = "0.1.0"
            authors = []

            [dependencies]
        """)

        dir("src") {
            rust("main.rs", """
                extern crate kebab_case;

                fn main() {
                    kebab_case::foo();
                }              //^
            """)

            rust("lib.rs", "pub fn foo() { }")
        }
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

            dir("src") {
                rust("lib.rs", "mod foo; mod bar;")
                rust("FOO.rs", "pub struct Spam;")
                rust("BAR.rs", """
                    use foo::Spam;
                             //^
                """)
            }
        }.checkReferenceIsResolved<RsPath>("src/BAR.rs")
    }

    // Test that we don't choke on winapi crate, which uses **A LOT** of
    // glob imports and is just **ENORMOUS**
    fun `test winapi torture (value)`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [dependencies]
            winapi = "0.2"
        """)

        dir("src") {
            rust("main.rs", """
                extern crate winapi;
                use winapi::*;

                fn main() {
                    let _ = foo;
                }          //^
            """)
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)

    fun `test winapi torture (type)`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [dependencies]
            winapi = "0.2"
        """)

        dir("src") {
            rust("main.rs", """
                extern crate winapi;
                use winapi::*;

                fn main() {
                    let a: Foo;
                }         //^
            """)
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)

    fun `test multiversion crate resolve`() = buildProject {
        toml("Cargo.toml", """
            [workspace]
            members = ["hello"]
        """)
        dir("hello") {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                workspace = "../"

                [dependencies]
                rand = "=0.3.14"
                bar = { version = "7.0.0", path = "../bar" }
            """)
            dir("src") {
                rust("main.rs", """
                    extern crate rand;
                    extern crate bar;
                    fn main() {
                        let _ = rand::thread_rng();
                                      //^
                    }
                """)
            }
        }
        dir("bar") {
            toml("Cargo.toml", """
                [package]
                name = "bar"
                version = "7.0.0"

                [dependencies]
                rand = { version = "54.0.0", path="../rand" }
            """)
            dir("src") {
                rust("lib.rs", """
                    extern crate rand;
                    fn bar() {
                        let _ = rand::thread_rng();
                                      //^
                    }
                """)
            }
        }
        dir("rand") {
            toml("Cargo.toml", """
                [package]
                name = "rand"
                version = "54.0.0"
            """)
            dir("src") {
                rust("lib.rs", """
                    pub fn thread_rng() -> u32 { 42 }
            """)
            }
        }
    }.run {
        checkReferenceIsResolved<RsPath>("hello/src/main.rs", toCrate = "rand 0.3.14")
        checkReferenceIsResolved<RsPath>("bar/src/lib.rs", toCrate = "rand 54.0.0")
    }

    fun `test cargo rename`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
            my_log = { package = "log", version = "=0.4.7" }
        """)

        dir("src") {
            rust("main.rs", """
                use my_log::Log;
                    //^
            """)
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs", toCrate = "log 0.4.7")

    fun `test cargo rename of local dependency`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
            bar = { package = "foo", path = "./foo" }
        """)

        dir("src") {
            rust("main.rs", """
                use bar::foo;
                    //^
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
                    pub fn foo() {}
                """)
            }
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs")

    fun `test cargo rename of local dependency with custom lib target name`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
            bar = { package = "foo", path = "./foo" }
        """)

        dir("src") {
            rust("main.rs", """
                use bar::foo;
                    //^
            """)
        }

        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []

                [lib]
                name = "lib_foo"
            """)

            dir("src") {
                rust("lib.rs", """
                    pub fn foo() {}
                """)
            }
        }
    }.checkReferenceIsResolved<RsPath>("src/main.rs")

    fun `test custom target path`() {
        val libraryDir = tempDirFixture.getFile(".")!!
        val library = fileTree {
            dir("cargo") {
                toml("Cargo.toml", """
                    [package]
                    name = "foo"
                    version = "0.1.0"
                    authors = []

                    [lib]
                    path = "../lib.rs"
                """)
            }

            rust("lib.rs", """
                mod qqq {
                    pub fn baz() {}
                }
                pub mod bar;
            """)
            rust("bar.rs", """
                pub use super::qqq::baz;
            """)
        }.create(project, libraryDir)

        val libraryPath = FileUtil.toSystemIndependentName(library.root.pathAsPath.resolve("cargo").toString())
            .let { rustupFixture.toolchain?.toRemotePath(it) }
        val testProject = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                foo = { path = "$libraryPath" }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate foo;

                    use foo::bar::baz;

                    fn main() {
                        baz();
                        //^
                    }
                """)
            }
        }
        testProject.checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    fun `test disabled cfg feature`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"

            [dependencies]
            foo = { path = "./foo", features = [] }
        """)
        dir("src") {
            rust("main.rs", """
                extern crate foo;
                fn main() {
                    let _ = foo::bar();
                               //^
                }
            """)
        }
        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "1.0.0"

                [features]
                foobar = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(feature="foobar")]
                    pub fn bar() -> u32 { 42 }
                """)
            }
        }
    }.run {
        project.cargoProjects.singlePackage("foo").checkFeatureDisabled("foobar")
        checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)
    }

    fun `test enabled cfg feature`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"

            [dependencies]
            foo = { path = "./foo", features = ["foobar"] }
        """)
        dir("src") {
            rust("main.rs", """
                extern crate foo;
                fn main() {
                    let _ = foo::bar();
                               //^
                }
            """)
        }
        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "1.0.0"

                [features]
                foobar = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(feature="foobar")]
                    pub fn bar() -> u32 { 42 }
                """)
            }
        }
    }.run {
        project.cargoProjects.singlePackage("foo").checkFeatureEnabled("foobar")
        checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    fun `test enabled cfg feature in renamed package`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            edition = "2018"

            [dependencies]
            foo = { path = "./foo", features = ["foo_feature"] }
        """)
        dir("src") {
            rust("main.rs", """
                fn main() {
                    let _ = foo::bar();
                               //^
                }
            """)
        }
        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "1.0.0"
                edition = "2018"

                [features]
                foo_feature = ["bar_renamed/bar_feature"]

                [dependencies.bar_renamed]
                package = "bar"
                path = "../bar"
                optional = true
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(feature="bar_renamed")]
                    pub use bar_renamed::bar;
                """)
            }
        }
        dir("bar") {
            toml("Cargo.toml", """
                [package]
                name = "bar"
                version = "1.0.0"

                [features]
                bar_feature = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(feature="bar_feature")]
                    pub fn bar() -> u32 { 42 }
                """)
            }
        }
    }.run {
        project.cargoProjects.singlePackage("foo").checkFeatureEnabled("foo_feature")
        project.cargoProjects.singlePackage("foo").checkFeatureEnabled("bar_renamed")
        project.cargoProjects.singlePackage("bar").checkFeatureEnabled("bar_feature")
        checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    fun `test enabled cfg feature with changed target name`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            edition = "2018"

            [dependencies]
            foo = { path = "./foo", features = ["foo_feature"] }
        """)
        dir("src") {
            rust("main.rs", """
                fn main() {
                    let _ = foo::bar();
                               //^
                }
            """)
        }
        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "1.0.0"
                edition = "2018"

                [features]
                foo_feature = ["bar/bar_feature"]

                [dependencies]
                bar = { path = "../bar"}
            """)
            dir("src") {
                rust("lib.rs", """
                    pub use bar_target::bar;
                """)
            }
        }
        dir("bar") {
            toml("Cargo.toml", """
                [package]
                name = "bar"
                version = "1.0.0"

                [lib]
                path = "src/lib.rs"
                name = "bar_target"

                [features]
                bar_feature = []
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(feature="bar_feature")]
                    pub fn bar() -> u32 { 42 }
                """)
            }
        }
    }.run {
        project.cargoProjects.singlePackage("foo").checkFeatureEnabled("foo_feature")
        project.cargoProjects.singlePackage("bar").checkFeatureEnabled("bar_feature")
        checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    fun `test 2 cargo projects with common dependency with different features`() = fileTree {
        dir("project_1") {
            toml("Cargo.toml", """
                [package]
                name = "project_1"
                version = "0.1.0"
                authors = []

                [dependencies]
                common_dep = { path = "../common_dep", features = ["foo"] }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate common_dep;
                    fn main() {
                        common_dep::foo();
                    }              //^
                """)
            }
        }
        dir("project_2") {
            toml("Cargo.toml", """
                [package]
                name = "project_2"
                version = "0.1.0"
                authors = []

                [dependencies]
                common_dep = { path = "../common_dep", features = ["bar"] }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate common_dep;
                    fn main() {
                        common_dep::bar();
                    }              //^
                """)
            }
        }
        dir("common_dep") {
            toml("Cargo.toml", """
                [package]
                name = "common_dep"
                version = "0.1.0"
                authors = []

                [dependencies]

                [features]
                foo = []
                bar = []
            """)

            dir("src") {
                rust("lib.rs", """
                    #[cfg(feature = "foo")]
                    pub fn foo() {}
                    #[cfg(feature = "bar")]
                    pub fn bar() {}
                """)
            }
        }
    }.run {
        val prj = create(project, cargoProjectDirectory)
        project.testCargoProjects.attachCargoProjects(
            cargoProjectDirectory.pathAsPath.resolve("project_1/Cargo.toml"),
            cargoProjectDirectory.pathAsPath.resolve("project_2/Cargo.toml"),
        )
        prj.checkReferenceIsResolved<RsPath>("project_1/src/main.rs")
        prj.checkReferenceIsResolved<RsPath>("project_2/src/main.rs")
    }

    @UseOldResolve
    fun `test cyclic dev deps`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            build = "build.rs"

            [dev-dependencies]
            foo = { path = "./foo" }
        """)
        dir("tests") {
            rust("main.rs", """
                extern crate foo;
                fn main() {
                    foo::bar();
                }       //^
            """)
        }
        dir("src") {
            rust("lib.rs", """
                pub fn bar() {}
                #[test]
                fn test() {
                    extern crate foo;
                    foo::bar();
                }      //^
            """)
        }
        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "1.0.0"

                [dependencies]
                hello = { path = "../" }
            """)
            dir("src") {
                rust("lib.rs", """
                    extern crate hello;
                    pub use hello::bar;
                """)
            }
        }
    }.run {
        CrateGraphTestmarks.cyclicDevDependency.checkHit {
            checkReferenceIsResolved<RsPath>("tests/main.rs")
            checkReferenceIsResolved<RsPath>("src/lib.rs")
        }
    }

    fun `test build-dependency is resolved in 'build rs' and not resolved in 'main rs'`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            build = "build.rs"

            [build-dependencies]
            foo = { path = "./foo" }
        """)
        rust("build.rs", """
            extern crate foo;
            fn main() {
                foo::bar();
            }      //^
        """)
        dir("src") {
            rust("main.rs", """
                extern crate foo;
                fn main() {
                    foo::bar();
                }      //^
            """)
        }
        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "1.0.0"
            """)
            dir("src") {
                rust("lib.rs", """
                    pub fn bar() {}
                """)
            }
        }
    }.run {
        checkReferenceIsResolved<RsPath>("build.rs")
        checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)
    }

    fun `test normal dependency is not resolved in 'build rs' and resolved in 'main rs'`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            build = "build.rs"

            [dependencies]
            foo = { path = "./foo" }
        """)
        rust("build.rs", """
            extern crate foo;
            fn main() {
                foo::bar();
            }      //^
        """)
        dir("src") {
            rust("main.rs", """
                extern crate foo;
                fn main() {
                    foo::bar();
                }      //^
            """)
        }
        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "1.0.0"
            """)
            dir("src") {
                rust("lib.rs", """
                    pub fn bar() {}
                """)
            }
        }
    }.run {
        checkReferenceIsResolved<RsPath>("build.rs", shouldNotResolve = true)
        checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    fun `test stdlib in 'build rs'`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            build = "build.rs"
        """)
        rust("build.rs", """
            fn main() {
                std::mem::size_of::<i32>();
            }             //^
        """)
        dir("src") {
            rust("main.rs", "")
        }
    }.run {
        checkReferenceIsResolved<RsPath>("build.rs")
    }

    fun `test change crate name`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"

            [lib]
            name = "foo1"
        """)
        dir("src") {
            rust("main.rs", """
                fn main() {
                    foo2::func();
                }       //^
            """)
            rust("lib.rs", "pub fn func() {}")
        }
    }.run {
        val projectManifest = cargoProjectDirectory.findFileByRelativePath("Cargo.toml")!!
        checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)
        runWriteAction {
            VfsUtil.saveText(projectManifest, VfsUtil.loadText(projectManifest).replace("foo1", "foo2"))
        }
        project.testCargoProjects.refreshAllProjectsSync()
        checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    fun `test change dependency name`() = buildProject {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"

            [dependencies]
            foo1 = { path = "./foo", package = "foo" }
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {
                    foo2::func();
                }       //^
            """)
        }

        dir("foo") {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
            """)

            dir("src") {
                rust("lib.rs", "pub fn func() {}")
            }
        }
    }.run {
        val projectManifest = cargoProjectDirectory.findFileByRelativePath("Cargo.toml")!!
        checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)
        runWriteAction {
            val textOld = """foo1 = { path = "./foo", package = "foo" }"""
            val textNew = """foo2 = { path = "./foo", package = "foo" }"""
            VfsUtil.saveText(projectManifest, VfsUtil.loadText(projectManifest).replace(textOld, textNew))
        }
        project.testCargoProjects.refreshAllProjectsSync()
        checkReferenceIsResolved<RsPath>("src/main.rs")
    }

    // we test that there are no exceptions (and not resolve result)
    fun `test remove crate from workspace`() = buildProject {
        toml("Cargo.toml", """
            [workspace]
            members = [
                "foo1",
                "foo2"
            ]
        """)
        dir("foo1") {
            toml("Cargo.toml", """
                [package]
                name = "foo1"
                version = "0.1.0"
            """)
            dir("src") {
                rust("lib.rs", "")
            }
        }
        dir("foo2") {
            toml("Cargo.toml", """
                [package]
                name = "foo2"
                version = "0.1.0"
            """)
            dir("src") {
                rust("main.rs", """
                    fn main() {
                        func();
                    } //^
                    fn func() {}
                """)
            }
        }
    }.run {
        checkReferenceIsResolved<RsPath>("foo2/src/main.rs")
        val projectManifest = cargoProjectDirectory.findFileByRelativePath("Cargo.toml")!!
        runWriteAction {
            VfsUtil.saveText(projectManifest, VfsUtil.loadText(projectManifest).replace(""""foo2"""", ""))
        }
        project.testCargoProjects.refreshAllProjectsSync()
        checkReferenceIsResolved<RsPath>("foo2/src/main.rs")
    }

    private fun excludeDirectoryFromIndex(path: String) {
        runWriteAction {
            ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring {
                val module = ModuleUtilCore.findModuleForFile(cargoProjectDirectory, project)!!
                ModuleRootModificationUtil.updateModel(module) { rootModel ->
                    rootModel.contentEntries.singleOrNull()!!
                        .addExcludeFolder(FileUtil.join(cargoProjectDirectory.url, path))
                }
            }
        }
    }
}
