/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.psi.PsiElement
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMacroDefinition
import org.rust.lang.core.psi.ext.RsNamedElement


class RsCompletionPriorityTest : RsTestBase() {
    fun `test macros_are_low_priority`() {
        InlineFile("""
            fn foo_bar() {}
            macro_rules! foo_bar {}

            fn _foo_bar() {}
            macro_rules! _foo_bar {}

            fn main() {
                foo/*caret*/
            }
        """)

        val elements = myFixture.completeBasic()
            .map { it.psiElement!! as RsNamedElement }

        val expected = listOf(
            element<RsFunction>("foo_bar"),
            element<RsFunction>("_foo_bar"),
            element<RsMacroDefinition>("foo_bar"),
            element<RsMacroDefinition>("_foo_bar")
        )

        check(elements.size == expected.size)
        elements.zip(expected).forEach { (e, m) -> m(e) }
    }

    private inline fun <reified T : RsNamedElement> element(name: String) = { psi: PsiElement ->
        psi as? T ?: error("Expected a ${T::class.java.name}, got $psi")
        val actualName = psi.name
        check(name == actualName) { "Expected $name got $actualName" }
    }

}
