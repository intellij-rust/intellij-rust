package org.rust.ide.typing

class RustTypingMiscTests : RustTypingTestCaseBase() {
    override val dataPath: String = ""

    fun testIssue680() = doTestByText("foo.rs", "<caret>", "\n<caret>") // https://github.com/intellij-rust/intellij-rust/issues/680
}
