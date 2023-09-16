/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import com.intellij.util.ThrowableRunnable
import com.intellij.util.io.DataOutputStream
import org.rust.*
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.cargo.util.parseSemVer
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.experiments.RsExperiments.ATTR_PROC_MACROS
import org.rust.ide.experiments.RsExperiments.DERIVE_PROC_MACROS
import org.rust.ide.experiments.RsExperiments.FN_LIKE_PROC_MACROS
import org.rust.lang.core.macros.errors.ProcMacroExpansionError
import org.rust.lang.core.macros.errors.readMacroExpansionError
import org.rust.lang.core.macros.errors.writeMacroExpansionError
import org.rust.lang.core.macros.tt.TokenTree
import org.rust.lang.core.macros.tt.parseSubtree
import org.rust.lang.core.macros.tt.toDebugString
import org.rust.lang.core.parser.createRustPsiBuilder
import org.rust.openapiext.RsPathManager
import org.rust.stdext.RsResult
import org.rust.stdext.toPath
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import kotlin.io.path.exists

/**
 * A low-level test for proc macro expansion infrastructure
 */
@MinRustcVersion("1.46.0")
class RsProcMacroExpanderTest : RsTestBase() {
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun test() {
        val cargoProject = project.cargoProjects.singleProject()
        val pkg = cargoProject.workspaceOrFail().packages
            .find { it.name == WithProcMacros.TEST_PROC_MACROS }!!
        val lib = pkg.procMacroArtifact?.path?.toString()
            ?: error("Procedural macro artifact is not found. This most likely means a compilation failure")
        val toolchain = project.toolchain
            ?: error("Toolchain is not available")
        val expanderExecutable = cargoProject.procMacroExpanderPath
            ?: error("proc macro expander is not found")
        val rustcVersion = cargoProject.rustcInfo?.version?.semver
        val needsVersionCheck = rustcVersion != null
            && rustcVersion >= "1.70.0".parseSemVer()
        val server = ProcMacroServerPool.new(toolchain, needsVersionCheck, expanderExecutable, testRootDisposable)
        val expander = ProcMacroExpander.new(project, server)

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
            checkError<ProcMacroExpansionError.ProcessAborted>(lib, "function_like_process_exit", "")
            checkError<ProcMacroExpansionError.ProcessAborted>(lib, "function_like_process_abort", "")
            checkError<ProcMacroExpansionError.ProcessAborted>(lib, "function_like_do_brace_println_and_process_exit", "")
            checkError<ProcMacroExpansionError.IOExceptionThrown>(lib, "function_like_do_println_braces", "")
            checkError<ProcMacroExpansionError.IOExceptionThrown>(lib, "function_like_do_println_text_in_braces", "")
            checkExpandedAsIs(lib, "function_like_as_is", "") // Insure it works after errors
        }
    }

    fun `test CantRunExpander error`() {
        val toolchain = project.toolchain!!
        val nonExistingFile = "/non/existing/file".toPath()
        assertFalse(nonExistingFile.exists())
        val invalidServer = ProcMacroServerPool.new(toolchain, true, nonExistingFile, testRootDisposable)
        val expander = ProcMacroExpander.new(project, server = invalidServer)
        expander.checkError<ProcMacroExpansionError.CantRunExpander>("", "", "")
    }

    @WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
    fun `test ExecutableNotFound error`() {
        val expander = ProcMacroExpander.new(project, server = null)
        expander.checkError<ProcMacroExpansionError.ExecutableNotFound>("", "", "")
    }

    @WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS)
    @WithoutExperimentalFeatures(FN_LIKE_PROC_MACROS, DERIVE_PROC_MACROS, ATTR_PROC_MACROS)
    fun `test ProcMacroExpansionIsDisabled error 1`() {
        val expander = ProcMacroExpander.new(project, server = null)
        expander.checkError<ProcMacroExpansionError.ProcMacroExpansionIsDisabled>("", "", "")
    }

    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @WithoutExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS)
    fun `test ProcMacroExpansionIsDisabled error 2`() {
        val expander = ProcMacroExpander.new(project, server = null)
        expander.checkError<ProcMacroExpansionError.ProcMacroExpansionIsDisabled>("", "", "")
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
    ) = checkError(T::class.java, lib, name, macroCall)

    private fun ProcMacroExpander.checkError(
        errorClass: Class<*>,
        lib: String,
        name: String,
        macroCall: String
    ) {
        val result = expandMacroAsTtWithErr(project.createRustPsiBuilder(macroCall).parseSubtree().subtree, null, name, lib)
        check(errorClass.isInstance(result.err())) { "Expected error $errorClass, got result $result" }

        val bytes = ByteArrayOutputStream()
        val originError = result.err()!!
        DataOutputStream(bytes).use {
            it.writeMacroExpansionError(originError)
        }
        val restoredError = DataInputStream(ByteArrayInputStream(bytes.toByteArray())).use {
            it.readMacroExpansionError()
        }
        assertEquals(originError, restoredError)
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        if (RsPathManager.nativeHelper(project.toolchain is RsWslToolchain) == null &&
            System.getenv("CI") == null) {
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
