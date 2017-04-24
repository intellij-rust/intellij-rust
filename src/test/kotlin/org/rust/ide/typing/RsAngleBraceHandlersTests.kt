package org.rust.ide.typing

class RsAngleBraceHandlersTests : RsTypingTestBase() {
    override val dataPath: String = ""

    fun `test pair angle brace after colon colon token`() = doComplexTest("fn foo() { let _ = foo::<caret>")
    fun `test pair angle brace in implementation block`() = doComplexTest("impl<caret>")
    fun `test pair angle brace in generic function declaration`() = doComplexTest("fn foo<caret>")
    fun `test pair angle brace in generic struct declaration`() = doComplexTest("struct Foo<caret>")
    fun `test pair angle brace in generic enum declaration`() = doComplexTest("enum Foo<caret>")
    fun `test pair angle brace in generic trait declaration`() = doComplexTest("trait Foo<caret>")
    fun `test pair angle brace in generic type alias declaration`() = doComplexTest("type Foo<caret>")
    fun `test pair angle brace after type like identifier`() = doComplexTest("fn foo() -> Result<caret>")

    fun `test don't pair angle brace in the middle of identifier`() =
        doTestByText("enum Fo<caret>o", "enum Fo<<caret>o", '<')

    fun `test don't pair angle brace after not type like identifier`() =
        doTestByText("fn main() { if a<caret> }", "fn main() { if a<<caret> }", '<')

    fun `test don't pair angle after constant like identifier`() =
        doTestByText("const CONSTANT<caret>", "const CONSTANT<<caret>", '<')

    fun `test don't pair angle brace if braces aren't balanced`() =
        doTestByText("struct Foo<caret>>", "struct Foo<<caret>>", '<')

    fun `test don't remove next GT if braces aren't balanced`() =
        doTestByText("fn foo() { let _ = foo::<<<caret>>", "fn foo() { let _ = foo::<<caret>>", '\b')

    private fun doComplexTest(before: String) {
        val afterWithGT = before.replace("<caret>", "<<caret>>")
        // check completion at the end of file
        doTypeDeleteTest(before, afterWithGT)
        // check completion in the middle of file
        doTypeDeleteTest(before, afterWithGT, "mod Foo { <code> }")

        val afterWithoutGT = before.replace("<caret>", "<<caret>")
        // check completion in string literal
        doTypeDeleteTest(before, afterWithoutGT, """fn main() { let _ = "<code>" }""")
        // check completion in comments
        doTypeDeleteTest(before, afterWithoutGT, """fn main() { /* <code> */ }""")
    }

    private fun doTypeDeleteTest(before: String, after: String, surroundings: String) = doTypeDeleteTest(
        surroundings.replace("<code>", before),
        surroundings.replace("<code>", after)
    )

    private fun doTypeDeleteTest(before: String, after: String) {
        // First type a '<' sign...
        doTestByText(before, after, '<')
        // ...then delete it.
        doTestByText(after, before, '\b')
    }
}
