package org.rust.ide.typing

class RustEnterInLineCommentHandlerTest : RustTypingTestCaseBase() {
    override val dataPath = "org/rust/ide/typing/lineComment/fixtures"

    fun testBeforeLineComment() = doTest()
    fun testInLineComment() = doTest()
    fun testAfterLineComment() = doTest()
    fun testInBlockComment() = doTest()
    fun testInOuterDocComment() = doTest()
    fun testAfterOuterDocComment() = doTest()
    fun testInInnerDocComment() = doTest()
    fun testAfterInnerDocComment() = doTest()
    fun testAfterModuleComment() = doTest()

    fun testDirectlyAfterToken() = doTest()
    fun testInsideToken() = doTest()

    fun testInsideCommentDirectlyBeforeNextToken() = doTest()
    fun testInsideCommentInsideToken() = doTest()

    fun testAtFileBeginning() = doTest()
    fun testInsideStringLiteral() = doTest()

    fun testIssue578() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/578
}
