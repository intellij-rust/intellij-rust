/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import org.rust.*
import org.rust.ide.experiments.RsExperiments.ATTR_PROC_MACROS
import org.rust.ide.experiments.RsExperiments.DERIVE_PROC_MACROS
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.FN_LIKE_PROC_MACROS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.macros.RsMacroExpansionErrorTestBase
import org.rust.lang.core.macros.errors.GetMacroExpansionError

/**
 * A test for [org.rust.lang.core.macros.errors.GetMacroExpansionError.toUserViewableMessage]
 *
 * @see RsProcMacroExpanderTest
 */
@MinRustcVersion("1.46.0")
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
class RsProcMacroErrorTest : RsMacroExpansionErrorTestBase() {
    @WithoutExperimentalFeatures(PROC_MACROS, ATTR_PROC_MACROS)
    fun `test macro expansion is disabled`() = checkError<GetMacroExpansionError.ExpansionError>("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        //^ procedural macro expansion is not enabled
        fn foo() {}
    """)

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, DERIVE_PROC_MACROS, ATTR_PROC_MACROS)
    @WithoutExperimentalFeatures(FN_LIKE_PROC_MACROS)
    fun `test function-like macro expansion is disabled`() = checkError<GetMacroExpansionError.ExpansionError>("""
        use test_proc_macros::function_like_as_is;

        function_like_as_is! {}
        //^ procedural macro expansion is not enabled
    """)

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, FN_LIKE_PROC_MACROS, ATTR_PROC_MACROS)
    @WithoutExperimentalFeatures(DERIVE_PROC_MACROS)
    fun `test derive macro expansion is disabled`() = checkError<GetMacroExpansionError.ExpansionError>("""
        use test_proc_macros::DeriveImplForFoo;

        #[derive(DeriveImplForFoo)]
               //^ procedural macro expansion is not enabled
        struct Foo;
    """)

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, FN_LIKE_PROC_MACROS, DERIVE_PROC_MACROS)
    @WithoutExperimentalFeatures(ATTR_PROC_MACROS)
    fun `test attr macro expansion is disabled`() = checkError<GetMacroExpansionError.ExpansionError>("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        //^ procedural macro expansion is not enabled
        fn foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @WithoutExperimentalFeatures(PROC_MACROS, ATTR_PROC_MACROS)
    fun `test macro expansion is disabled with unsuccessfully compiled proc macro crate`() = checkErrorByTree<GetMacroExpansionError.ExpansionError>("""
    //- dep-proc-macro-unsuccessfully-compiled/lib.rs
        extern crate proc_macro;
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn attr_as_is(_attr: TokenStream, item: TokenStream) -> TokenStream {
           item
        }
        compile_error!("The crate with the macro is not compiled successfully");
    //- main.rs
        use dep_proc_macro_unsuccessfully_compiled::attr_as_is;

        #[attr_as_is]
        //^ procedural macro expansion is not enabled
        fn foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test unsuccessfully compiled proc macro crate`() = checkErrorByTree<GetMacroExpansionError.NoProcMacroArtifact>("""
    //- dep-proc-macro-unsuccessfully-compiled/lib.rs
        extern crate proc_macro;
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn attr_as_is(_attr: TokenStream, item: TokenStream) -> TokenStream {
           item
        }
        compile_error!("The crate with the macro is not compiled successfully");
    //- main.rs
        use dep_proc_macro_unsuccessfully_compiled::attr_as_is;

        #[attr_as_is]
        //^ the procedural macro is not compiled successfully
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
