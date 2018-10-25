/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

import org.intellij.lang.annotations.Language

class RsAttributeParenthesisTypedHandlerTest : RsTypingTestBase() {
    fun `test pair parenthesis in struct attribute`() = doComplexTest("#[derive<caret>] struct Foo")

    fun `test pair parenthesis in inner attribute`() =
        doTestByText("#[derive<caret>]", "#[derive(<caret>)]", '(')

    fun `test pair parenthesis in outer attribute`() =
        doTestByText("#![feature<caret>]", "#![feature(<caret>)]", '(')

    fun `test pair parenthesis in unclosed inner attribute block`() =
        doTestByText("#[derive<caret>", "#[derive(<caret>)", '(')

    fun `test pair parenthesis in unclosed outer attribute block`() =
        doTestByText("#![derive<caret>", "#![derive(<caret>)", '(')

    fun `test pair parenthesis with nested attributes`() =
        doTestByText("#[foo(bar<caret>)]", "#[foo(bar(<caret>))]", '(')

    fun `test pair parenthesis with multiple nested attributes`() =
        doTestByText("""#[foo(bar("hi"), baz<caret>)]""", """#[foo(bar("hi"), baz(<caret>))]""", '(')

    fun `test advance cursor when closing balanced parenthesis`() =
        doTestByText("#[foo(<caret>)]", "#[foo()<caret>]", ')')

    fun `test no advancing cursor when closing unbalanced parenthesis`() =
        doTestByText("#[foo(<caret>]", "#[foo()<caret>]", ')')

    fun `test no pair parenthesis inside identifier`() =
        doTestByText("#![plu<caret>gin]", "#![plu(<caret>gin]", '(')

    fun `test empty close`() =
        doTestByText("<caret>", ")<caret>", ')')

    private fun doComplexTest(@Language("Rust") before: String) {
        val afterWithRPAREN = before.replace("<caret>", "(<caret>)")
        // check completion at the end of file
        doTypeDeleteTest(before, afterWithRPAREN)
        // check completion in the middle of file
        doTypeDeleteTest(before, afterWithRPAREN, "mod Foo { <code> }")

        val afterWithoutRPAREN = before.replace("<caret>", "(<caret>")
        // check completion in string literal
        doTypeDeleteTest(before, afterWithoutRPAREN, """fn main() { let _ = "<code>" }""")
        // check completion in comments
        doTypeDeleteTest(before, afterWithoutRPAREN, """fn main() { /* <code> */ }""")
    }

    private fun doTypeDeleteTest(@Language("Rust") before: String, @Language("Rust") after: String, @Language("Rust") surroundings: String) = doTypeDeleteTest(
        surroundings.replace("<code>", before),
        surroundings.replace("<code>", after)
    )

    private fun doTypeDeleteTest(before: String, after: String) {
        // First type a '('...
        doTestByText(before, after, '(')
        // ...then delete it.
        doTestByText(after, before, '\b')
    }
}
