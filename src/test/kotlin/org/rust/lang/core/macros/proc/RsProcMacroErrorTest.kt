/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.errors.GetMacroExpansionError
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.expansionResult
import org.rust.lang.core.psi.ext.isMacroCall
import org.rust.stdext.RsResult

/**
 * A test for [org.rust.lang.core.macros.errors.GetMacroExpansionError.toUserViewableMessage]
 *
 * @see RsProcMacroExpanderTest
 */
@MinRustcVersion("1.46.0")
@ExpandMacros(MacroExpansionScope.WORKSPACE)
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
class RsProcMacroErrorTest : RsTestBase() {
    @WithExperimentalFeatures()
    fun `test macro expansion is disabled`() = checkError<GetMacroExpansionError.ExpansionError>("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        //^ procedural macro expansion is not enabled
        fn foo() {}
    """)

    fun `test unresolved function-like macro`() = checkError<GetMacroExpansionError.Unresolved>("""
        unresolved_macro! {}
    """)

    fun `test unresolved attribute macro`() = checkError<GetMacroExpansionError.Unresolved>("""
        #[unresolved_macro]
        fn foo() {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg-disabled function-like macro`() = checkError<GetMacroExpansionError.CfgDisabled>("""
        use test_proc_macros::function_like_as_is;

        #[cfg(not(intellij_rust))]
        function_like_as_is! {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg-disabled attribute macro`() = checkError<GetMacroExpansionError.CfgDisabled>("""
        use test_proc_macros::attr_as_is;

        #[cfg(not(intellij_rust))]
        #[attr_as_is]
        fn foo() {}
    """)

    fun `test hardcoded not a macro fn`() = checkError<GetMacroExpansionError.Skipped>("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        fn foo() {}
    """)

    fun `test hardcoded function-like macro used as an attribute`() = checkError<GetMacroExpansionError.UnmatchedProcMacroKind>("""
        use test_proc_macros::function_like_as_is;

        #[function_like_as_is]
        fn foo() {} //^ `FUNCTION_LIKE` proc macro can't be called as `ATTRIBUTE`
    """)

    private inline fun <reified T : GetMacroExpansionError> checkError(
        @Language("Rust") code: String
    ) {
        checkError(code, T::class.java)
    }

    private fun checkError(code: String, errorClass: Class<*>) {
        InlineFile(code)
        val markers = findElementsWithDataAndOffsetInEditor<RsPossibleMacroCall>()
        val (macro, expectedErrorMessage) = if (markers.isEmpty()) {
            myFixture.file
                .descendantsOfType<RsPossibleMacroCall>()
                .single { it.isMacroCall } to null
        } else {
            val (macro, message, _) = markers.single()
            check(macro.isMacroCall)
            macro to message
        }

        val err = when (val result = macro.expansionResult) {
            is RsResult.Err -> result.err
            is RsResult.Ok -> error("Expected a macro expansion error, got a successfully expanded macro")
        }

        check(errorClass.isInstance(err)) { "Expected error $errorClass, got $err" }

        if (expectedErrorMessage != null) {
            TestCase.assertEquals(expectedErrorMessage, err.toUserViewableMessage())
        }
    }
}
