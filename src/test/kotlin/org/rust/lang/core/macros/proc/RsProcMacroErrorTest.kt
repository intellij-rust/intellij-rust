/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import org.rust.*
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.RsMacroExpansionErrorTestBase
import org.rust.lang.core.macros.errors.GetMacroExpansionError

/**
 * A test for [org.rust.lang.core.macros.errors.GetMacroExpansionError.toUserViewableMessage]
 *
 * @see RsProcMacroExpanderTest
 */
@MinRustcVersion("1.46.0")
@ExpandMacros(MacroExpansionScope.WORKSPACE)
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
class RsProcMacroErrorTest : RsMacroExpansionErrorTestBase() {
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
}
