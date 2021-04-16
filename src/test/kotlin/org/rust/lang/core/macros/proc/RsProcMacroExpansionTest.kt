/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.exists
import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.ProcMacroExpansionError
import org.rust.lang.core.macros.tt.TokenTree
import org.rust.lang.core.macros.tt.parseSubtree
import org.rust.lang.core.macros.tt.toDebugString
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.runWithEnabledFeatures
import org.rust.singleWorkspace
import org.rust.stdext.RsResult
import org.rust.stdext.toPath

@MinRustcVersion("1.46.0")
class RsProcMacroExpansionTest : RsWithToolchainTestBase() {
    fun test(): Unit = runWithEnabledFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS) {
        buildProject {
            toml("Cargo.toml", """
                [workspace]
                members = ["my_proc_macro", "mylib"]
            """)
            dir("my_proc_macro") {
                toml("Cargo.toml", """
                    [package]
                    name = "my_proc_macro"
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
                        pub fn as_is(input: TokenStream) -> TokenStream {
                            return input;
                        }

                        #[proc_macro]
                        pub fn read_env_var(input: TokenStream) -> TokenStream {
                            use std::fmt::Write;
                            let v = std::env::var("FOO_ENV_VAR").unwrap();
                            let mut s = String::new();
                            write!(&mut s, "\"{}\"", v);
                            return s.parse().unwrap();
                        }

                        #[proc_macro]
                        pub fn do_println(input: TokenStream) -> TokenStream {
                            println!("foobar");
                            input
                        }

                        #[proc_macro]
                        pub fn do_eprintln(input: TokenStream) -> TokenStream {
                            eprintln!("foobar");
                            input
                        }

                        #[proc_macro]
                        pub fn do_panic(input: TokenStream) -> TokenStream {
                            panic!("panic message");
                        }

                        #[proc_macro]
                        pub fn wait_100_seconds(input: TokenStream) -> TokenStream {
                            std::thread::sleep(std::time::Duration::from_secs(100));
                            return input;
                        }

                        #[proc_macro]
                        pub fn process_exit(input: TokenStream) -> TokenStream {
                            std::process::exit(101)
                        }

                        #[proc_macro]
                        pub fn process_abort(input: TokenStream) -> TokenStream {
                            std::process::abort()
                        }
                    """)
                }
            }
            dir("mylib") {
                toml("Cargo.toml", """
                    [package]
                    name = "mylib"
                    version = "1.0.0"

                    [dependencies]
                    my_proc_macro = { path = "../my_proc_macro" }
                """)
                dir("src") {
                    rust("lib.rs", "")
                }
            }
        }
        val pkg = project.cargoProjects.singleWorkspace().packages
            .find { it.name == "my_proc_macro" }!!
        val lib = pkg.procMacroArtifact?.path?.toString()
            ?: error("Procedural macro artifact is not found. This most likely means a compilation failure")
        val server = ProcMacroServerPool.tryCreate(testRootDisposable)
            ?: error("native-helper is not available")
        val expander = ProcMacroExpander(project, server)

        with(expander) {
            checkExpandedAsIs(lib, "as_is", "")
            checkExpandedAsIs(lib, "as_is", ".")
            checkExpandedAsIs(lib, "as_is", "..")
            checkExpandedAsIs(lib, "as_is", "fn foo() {}")
            checkExpandedAsIs(lib, "as_is", "\"Привет\"") // "Hello" in russian, a test for non-ASCII chars
            checkExpansion(lib, "read_env_var", "", "\"foo value\"", env = mapOf("FOO_ENV_VAR" to "foo value"))
            checkExpandedAsIs(lib, "do_println", "")
            checkExpandedAsIs(lib, "do_eprintln", "")
            checkError<ProcMacroExpansionError.ServerSideError>(lib, "do_panic", "")
            checkError<ProcMacroExpansionError.ServerSideError>(lib, "unknown_macro", "")
            checkError<ProcMacroExpansionError.Timeout>(lib, "wait_100_seconds", "")
            checkError<ProcMacroExpansionError.ExceptionThrown>(lib, "process_exit", "")
            checkError<ProcMacroExpansionError.ExceptionThrown>(lib, "process_abort", "")
            checkExpandedAsIs(lib, "as_is", "") // Insure it works after errors
        }
    }

    fun `test CantRunExpander error`() {
        val nonExistingFile = myFixture.tempDirPath.toPath().resolve("non/existing/file")
        assertFalse(nonExistingFile.exists())
        val invalidServer = ProcMacroServerPool.createUnchecked(nonExistingFile, testRootDisposable)
        val expander = ProcMacroExpander(project, invalidServer)
        expander.checkError<ProcMacroExpansionError.CantRunExpander>("", "", "")
    }

    fun `test ExecutableNotFound error`() {
        val expander = ProcMacroExpander(project, null)
        expander.checkError<ProcMacroExpansionError.ExecutableNotFound>("", "", "")
    }

    private fun ProcMacroExpander.checkExpandedAsIs(
        lib: String,
        name: String,
        macroBodyAndExpansion: String,
    ) = checkExpansion(lib, name, macroBodyAndExpansion, macroBodyAndExpansion, checkTokenIds = true)

    private fun ProcMacroExpander.checkExpansion(
        lib: String,
        name: String,
        macroCall: String,
        expected: String,
        env: Map<String, String> = emptyMap(),
        checkTokenIds: Boolean = false
    ) {
        val macroCallSubtree = project.createRustPsiBuilder(macroCall).parseSubtree().subtree
        val expansionTtWithIds = when (val expansionResult = expandMacroAsTtWithErr(macroCallSubtree, name, lib, env)) {
            is RsResult.Ok -> expansionResult.ok
            is RsResult.Err -> error("Expanded with error: ${expansionResult.err}")
        }
        val expectedTtWithIds = project.createRustPsiBuilder(expected).parseSubtree().subtree
        val expectedTt = if (checkTokenIds) expectedTtWithIds else expectedTtWithIds.discardIds()
        val expansionTt = if (checkTokenIds) expansionTtWithIds else expansionTtWithIds.discardIds()
        assertEquals(
            expectedTt.toDebugString(),
            expansionTt.toDebugString()
        )
    }

    private inline fun <reified T : ProcMacroExpansionError> ProcMacroExpander.checkError(
        lib: String,
        name: String,
        macroCall: String
    ) {
        val result = expandMacroAsTtWithErr(project.createRustPsiBuilder(macroCall).parseSubtree().subtree, name, lib)
        check(result.err() is T) { "Expected error ${T::class}, got result $result" }
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        if (RsPathManager.nativeHelper() == null && System.getenv("CI") == null) {
            System.err.println("SKIP \"$name\": no native-helper executable")
            return
        }
        super.runTestRunnable(testRunnable)
    }

    private fun TokenTree.discardIds(): TokenTree = when (this) {
        is TokenTree.Leaf -> discardIds()
        is TokenTree.Subtree -> discardIds()
    }

    private fun TokenTree.Subtree.discardIds(): TokenTree.Subtree = TokenTree.Subtree(
        delimiter?.copy(id = -1),
        tokenTrees.map { it.discardIds() }
    )

    private fun TokenTree.Leaf.discardIds(): TokenTree.Leaf = when (this) {
        is TokenTree.Leaf.Ident -> copy(id = -1)
        is TokenTree.Leaf.Literal -> copy(id = -1)
        is TokenTree.Leaf.Punct -> copy(id = -1)
    }
}
