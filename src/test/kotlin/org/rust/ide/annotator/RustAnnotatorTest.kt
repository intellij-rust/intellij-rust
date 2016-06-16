package org.rust.ide.annotator

import org.rust.lang.RustTestCaseBase

class RustAnnotatorTest : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures"

    fun testAttributes() = doTestInfo()
    fun testMacro() = doTestInfo()
    fun testTypeParameters() = doTestInfo()
    fun testMutBinding() = doTestInfo()
    fun testFunctions() = doTestInfo()

    fun testCharLiteralLength() = doTest()
    fun testLiteralSuffixes() = doTest()
    fun testLiteralUnclosedQuotes() = doTest()

    fun testUnnecessaryIfParens() = doTest()
    fun testUnnecessaryForParens() = doTest()
    fun testUnnecessaryMatchParens() = doTest()
    fun testUnnecessaryReturnParens() = doTest()
    fun testUnnecessaryWhileParens() = doTest()
    fun testRedundantParens() = doTest()

    fun testPaths() = doTest()

    fun testInvalidPub() = doTest()

    private fun doTest() {
        myFixture.testHighlighting(fileName)
    }

    private fun doTestInfo() {
        myFixture.testHighlighting(false, true, false, fileName)
    }
}
