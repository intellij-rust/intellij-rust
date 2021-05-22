/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.exists
import org.rust.*
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.macros.ProcMacroExpansionError
import org.rust.lang.core.macros.tt.TokenTree
import org.rust.lang.core.macros.tt.parseSubtree
import org.rust.lang.core.macros.tt.toDebugString
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.openapiext.RsPathManager
import org.rust.stdext.RsResult
import org.rust.stdext.toPath

/**
 * A low-level test for proc macro expansion infrastructure
 */
@MinRustcVersion("1.46.0")
class RsProcMacroExpanderTest : RsTestBase() {
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun test() {
        val pkg = project.cargoProjects.singleWorkspace().packages
            .find { it.name == WithProcMacros.TEST_PROC_MACROS }!!
        val lib = pkg.procMacroArtifact?.path?.toString()
            ?: error("Procedural macro artifact is not found. This most likely means a compilation failure")
        val server = ProcMacroServerPool.tryCreate(testRootDisposable)
            ?: error("native-helper is not available")
        val expander = ProcMacroExpander(project, server)

        with(expander) {
            checkExpandedAsIs(lib, "function_like_as_is", "")
            checkExpandedAsIs(lib, "function_like_as_is", ".")
            checkExpandedAsIs(lib, "function_like_as_is", "..")
            checkExpandedAsIs(lib, "function_like_as_is", "fn foo() {}")
            checkExpandedAsIs(lib, "function_like_as_is", "\"Привет\"") // "Hello" in russian, a test for non-ASCII chars
            checkExpansion(lib, "function_like_read_env_var", "", "\"foo value\"", env = mapOf("FOO_ENV_VAR" to "foo value"))
            checkExpandedAsIs(lib, "function_like_do_println", "")
            checkExpandedAsIs(lib, "function_like_do_eprintln", "")
            checkError<ProcMacroExpansionError.ServerSideError>(lib, "function_like_do_panic", "")
            checkError<ProcMacroExpansionError.ServerSideError>(lib, "unknown_macro", "")
            checkError<ProcMacroExpansionError.Timeout>(lib, "function_like_wait_100_seconds", "")
            checkError<ProcMacroExpansionError.ExceptionThrown>(lib, "function_like_process_exit", "")
            checkError<ProcMacroExpansionError.ExceptionThrown>(lib, "function_like_process_abort", "")
            checkExpandedAsIs(lib, "function_like_as_is", "") // Insure it works after errors
        }
    }

    fun `test CantRunExpander error`() {
        val nonExistingFile = "/non/existing/file".toPath()
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
        val expansionTtWithIds = when (val expansionResult = expandMacroAsTtWithErr(macroCallSubtree, null, name, lib, env)) {
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
        val result = expandMacroAsTtWithErr(project.createRustPsiBuilder(macroCall).parseSubtree().subtree, null, name, lib)
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
