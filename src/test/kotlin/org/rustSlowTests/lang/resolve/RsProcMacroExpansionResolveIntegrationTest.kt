/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.lang.resolve

import com.intellij.util.ThrowableRunnable
import org.rust.*
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.psi.RsMethodCall
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.pathAsPath

@MinRustcVersion("1.46.0")
@ExpandMacros(MacroExpansionScope.WORKSPACE)
@WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
class RsProcMacroExpansionResolveIntegrationTest : RsWithToolchainTestBase() {
    fun `test 2 cargo projects (proc macro is a separate cargo project)`() {
        fileTree {
            localProcMacroLib()
            dir("mylib") {
                toml("Cargo.toml", """
                    [package]
                    name = "mylib"
                    version = "1.0.0"
                    edition = "2018"

                    [dependencies]
                    proc-macro-id = { path = "../proc-macro-id" }
                """)
                dir("src") {
                    rust("lib.rs", """
                        use proc_macro_id::id;

                        struct Foo;
                        impl Foo {
                            fn bar(&self) {}
                        }     //X

                        id! {
                            fn foo() -> Foo { Foo }
                        }

                        fn main() {
                            foo().bar()
                        }       //^
                    """)
                }
            }
        }.run {
            val prj = create(project, cargoProjectDirectory)
            project.testCargoProjects.attachCargoProjects(
                cargoProjectDirectory.pathAsPath.resolve("proc-macro-id/Cargo.toml"),
                cargoProjectDirectory.pathAsPath.resolve("mylib/Cargo.toml")
            )
            prj.checkReferenceIsResolved<RsMethodCall>("mylib/src/lib.rs")
        }
    }

    fun `test from crates_io`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "mylib"
                version = "1.0.0"
                edition = "2018"

                [dependencies]
                proc-macro-id = "=1.0.1"
            """)
            dir("src") {
                rust("lib.rs", """
                    use proc_macro_id::id;

                    struct Foo;
                    impl Foo {
                        fn bar(&self) {}
                    }     //X

                    id! {
                        fn foo() -> Foo { Foo }
                    }

                    fn main() {
                        foo().bar()
                    }       //^
                """)
            }
        }.checkReferenceIsResolved<RsMethodCall>("src/lib.rs")
    }

    fun `test dev-dependency`() {
        buildProject {
            localProcMacroLib()
            toml("Cargo.toml", """
                [package]
                name = "mylib"
                version = "1.0.0"
                edition = "2018"

                [dev-dependencies]
                proc-macro-id = { path = "proc-macro-id" }
            """)
            dir("src") {
                rust("lib.rs", """
                    #[cfg(test)]
                    mod tests {
                        use proc_macro_id::id;

                        struct Foo;
                        impl Foo {
                            fn bar(&self) {}
                        }     //X

                        id! {
                            fn foo() -> Foo { Foo }
                        }

                        fn test() {
                            foo().bar()
                        }       //^
                    }
                """)
            }
        }.checkReferenceIsResolved<RsMethodCall>("src/lib.rs")
    }

    fun `test compilation error in workspace`() {
        buildProject {
            localProcMacroLib()
            toml("Cargo.toml", """
                [package]
                name = "mylib"
                version = "1.0.0"
                edition = "2018"

                [dependencies]
                proc-macro-id = { path = "proc-macro-id" }
            """)
            rust("build.rs", """
                fn main() { // compilation error
            """)
            dir("src") {
                rust("lib.rs", """
                    use proc_macro_id::id;

                    struct Foo;
                    impl Foo {
                        fn bar(&self) {}
                    }     //X

                    id! {
                        fn foo() -> Foo { Foo }
                    }

                    fn test() {
                        foo().bar()
                    }       //^
                """)
            }
        }.checkReferenceIsResolved<RsMethodCall>("src/lib.rs")
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        if (RsPathManager.nativeHelper(rustupFixture.toolchain is RsWslToolchain) == null &&
            System.getenv("CI") == null) {
            System.err.println("SKIP \"$name\": no native-helper executable")
            return
        }
        super.runTestRunnable(testRunnable)
    }

    /**
     * Add local `proc-macro-id` crate into [dirName] directory that provides identity `id` function-like procedural macros
     */
    private fun FileTreeBuilder.localProcMacroLib(dirName: String = "proc-macro-id") {
        dir(dirName) {
            toml("Cargo.toml", """
                [package]
                name = "proc-macro-id"
                version = "1.0.0"
                edition = "2018"

                [lib]
                proc-macro = true

                [dependencies]
            """)
            dir("src") {
                rust("lib.rs", """
                    extern crate proc_macro;
                    use proc_macro::TokenStream;

                    #[proc_macro]
                    pub fn id(input: TokenStream) -> TokenStream {
                        return input;
                    }
                """)
            }
        }
    }
}
