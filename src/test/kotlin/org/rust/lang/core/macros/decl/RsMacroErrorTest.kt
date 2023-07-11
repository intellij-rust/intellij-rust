/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.decl

import org.rust.MockAdditionalCfgOptions
import org.rust.lang.core.macros.RsMacroExpansionErrorTestBase
import org.rust.lang.core.macros.errors.GetMacroExpansionError

class RsMacroErrorTest : RsMacroExpansionErrorTestBase() {
    // https://github.com/intellij-rust/intellij-rust/pull/2583
    fun `test empty group definition`() = checkError<GetMacroExpansionError.ExpansionError>("""
        macro_rules! foo {
            ($()* $ i:tt) => {  }
        }
        foo! { bar baz }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg disabled top level macro call`() = checkError<GetMacroExpansionError.CfgDisabled>("""
        macro_rules! foo {
            () => { fn foo() {} }
        }
        #[cfg(not(intellij_rust))]
        foo! {}
    """)

    fun `test macro inside an impl`() = checkError<GetMacroExpansionError.ExpansionError>("""
        macro_rules! foo {
            (bar) => { fn foo() {} };
        }

        struct S;
        impl S {
            foo! {}
        }
    """)
}
