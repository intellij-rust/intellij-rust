/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.builtin

import org.rust.lang.core.macros.*
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.contextToSetForExpansion
import org.rust.lang.core.resolve2.resolveToMacroWithoutPsi
import org.rust.stdext.unwrapOrElse

class RsBuiltinMacroExpansionTest : RsMacroExpansionTestBase() {
    override fun expandMacroOrFail(call: RsPossibleMacroCall): MacroExpansion {
        require(call is RsMacroCall)
        val def = call.resolveToMacroWithoutPsi().ok()?.data as? RsBuiltinMacroData
            ?: error("Failed to resolve macro `${call.path.text}`")

        val expander = BuiltinMacroExpander(project)
        val expansionResult = expander.expandMacro(
            RsMacroDataWithHash(def, null),
            call,
            storeRangeMap = true,
            useCache = false
        )
        val result = expansionResult.map {
            it.elements.forEach { el ->
                el.setContext(call.contextToSetForExpansion as RsElement)
                el.setExpandedFrom(call)
            }
            it
        }

        return result.unwrapOrElse {
            error("Failed to expand macro `${call.path.text}`")
        }
    }

    fun `test format_args`() = checkSingleMacro("""
        #[rustc_builtin_macro]
        macro_rules! format_args {}

        fn foo() {
            let a = 1;
            format_args!("{a}", b = 5);
            //^
        }
    """, """
        format_args!("{a}", b = 5, a = a)
    """)

    fun `test format_args_nl`() = checkSingleMacro("""
        #[rustc_builtin_macro]
        macro_rules! format_args_nl {}

        fn foo() {
            let a = 1;
            format_args_nl!("{a}", b = 5);
            //^
        }
    """, """
        format_args_nl!("{a}", b = 5, a = a)
    """)

    fun `test format_args no expansion when named parameter is present`() = doErrorTest("""
        #[rustc_builtin_macro]
        macro_rules! format_args {}

        fn foo() {
            format_args!("{a}", a = 5);
            //^
        }
    """)

    fun `test format_args expand multiple parameters`() = checkSingleMacro("""
        #[rustc_builtin_macro]
        macro_rules! format_args {}

        fn foo() {
            format_args!("{} {b} {a} {c}", 3, a = 5);
            //^
        }
    """, """
        format_args!("{} {b} {a} {c}", 3, a = 5, b = b, c = c)
    """)

    fun `test format_args trailing comma`() = checkSingleMacro("""
        #[rustc_builtin_macro]
        macro_rules! format_args {}

        fn foo() {
            format_args!("{a}",);
            //^
        }
    """, """
        format_args!("{a}", a = a)
    """)

    fun `test format_args trailing comma and space`() = checkSingleMacro("""
        #[rustc_builtin_macro]
        macro_rules! format_args {}

        fn foo() {
            format_args!("{a}", );
            //^
        }
    """, """
        format_args!("{a}", a = a)
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/9282
    fun `test incorrect syntax`() = doErrorTest("""
        #[rustc_builtin_macro]
        macro_rules! format_args {}

        fn foo() {
            let a = 2;
            format_args!("{a+5}");
            //^
        }
    """)

    fun `test self`() = checkSingleMacro("""
        #[rustc_builtin_macro]
        macro_rules! format_args {}

        fn foo() {
            format_args!("{self}");
            //^
        }
    """, """
        format_args!("{self}", self = self)
    """)
}
