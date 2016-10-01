package org.rust.ide.typing

class RustTypingMiscTests : RustTypingTestCaseBase() {
    override val dataPath: String = ""

    fun testIssue680() = doTestByText("foo.rs", "<caret>", "\n<caret>", '\n') // https://github.com/intellij-rust/intellij-rust/issues/680

    // https://github.com/intellij-rust/intellij-rust/issues/700
    fun testIssue700a() = doTestByText("foo.rs", "<caret>", "#<caret>", '#')
    fun testIssue700b() = doTestByText("foo.rs", "#<caret>", "<caret>", '\b')
}
