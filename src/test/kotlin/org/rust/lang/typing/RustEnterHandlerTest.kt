package org.rust.lang.typing

import org.rust.lang.RustTestCaseBase

class RustEnterHandlerTest : RustTestCaseBase() {

    override val dataPath = "org/rust/lang/typing/fixtures"

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
}
