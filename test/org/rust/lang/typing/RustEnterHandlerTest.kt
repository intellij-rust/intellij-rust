package org.rust.lang.typing

import org.rust.lang.RustTestCase

class RustEnterHandlerTest : RustTestCase() {
    override fun getTestDataPath() = "testData/typing"

    private fun doTest() {
        myFixture.configureByFile(fileName)
        myFixture.type('\n')
        myFixture.checkResultByFile(fileName.replace(".rs", "_after.rs"), true)
    }

    fun testInLineComment() = doTest()
    fun testAfterLineComment() = doTest()
    fun testInBlockComment() = doTest()
    fun testInOuterDocComment() = doTest()
    fun testAfterOuterDocComment() = doTest()
    fun testInInnerDocComment() = doTest()
    fun testAfterInnerDocComment() = doTest()
}
