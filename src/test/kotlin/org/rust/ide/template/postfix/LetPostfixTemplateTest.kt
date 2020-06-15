/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.refactoring.ExtractExpressionUi
import org.rust.ide.refactoring.withMockTargetExpressionChooser
import org.rust.lang.core.psi.RsExpr

class LetPostfixTemplateTest : RsPostfixTemplateTest(LetPostfixTemplate(RsPostfixTemplateProvider())) {
    fun `test not expr`() = doTestNotApplicable("""
        fn foo() {
            println!("test");.let/*caret*/
        }
    """)

    fun `test simple expr`() = doTest("""
        fn foo() {
            4.let/*caret*/;
        }
    """, """
        fn foo() {
            let /*caret*/i = 4;
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test par expr`() = doTest("""
        fn foo() {
            (1 + 2).let/*caret*/;
        }
    """, """
        fn foo() {
            let /*caret*/i = (1 + 2);
        }
    """)

    fun `test method call expr 1`() = doTest("""
        fn foo() { }

        fn main() {
            foo().let/*caret*/
        }
    """, """
        fn foo() { }

        fn main() {
            let /*caret*/foo1 = foo();
        }
    """)

    fun `test method call expr 2`() = doTest("""
        fn foo() -> i32 { 42 }

        fn main() {
            foo().let/*caret*/
        }
    """, """
        fn foo() -> i32 { 42 }

        fn main() {
            let /*caret*/i = foo();
        }
    """)

    fun `test replace all occurrences`() = doMultipleOccurrencesTest("""
        fn foo() {
            4.let/*caret*/;
            let a = 4;
            let b = 4;
        }
    """, """
        fn foo() {
            let /*caret*/i = 4;
            let a = i;
            let b = i;
        }
    """)

    private fun doMultipleOccurrencesTest(@Language("Rust") before: String, @Language("Rust") after: String) {
        withMockTargetExpressionChooser(object : ExtractExpressionUi {
            override fun chooseTarget(exprs: List<RsExpr>): RsExpr = error("unreachable")
            override fun chooseOccurrences(expr: RsExpr, occurrences: List<RsExpr>): List<RsExpr> = occurrences
        }) {
            doTest(before, after)
        }
    }
}
