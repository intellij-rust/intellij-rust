package org.rust.ide.typing

import org.rust.lang.RustTestCaseBase

class RustEnterInLineCommentHandlerTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/typing/fixtures"

    private fun doTest() {
        myFixture.configureByFile(fileName)
        myFixture.type('\n')
        myFixture.checkResultByFile(fileName.replace(".rs", "_after.rs"), true)
    }

    fun testBeforeLineComment() = doTest()
    fun testInLineComment() = doTest()
    fun testAfterLineComment() = doTest()
    fun testInBlockComment() = doTest()
    fun testInOuterDocComment() = doTest()
    fun testAfterOuterDocComment() = doTest()
    fun testInInnerDocComment() = doTest()
    fun testAfterInnerDocComment() = doTest()

    fun testDirectlyAfterToken() = doTest()
    fun testInsideToken() = doTest()

    fun testInsideCommentDirectlyBeforeNextToken() = doTest()
    fun testInsideCommentInsideToken() = doTest()

    fun testAtFileBeginning() = doTest()
    fun testInsideStringLiteral() = doTest()

    fun testIssue578() = doTest()
}
