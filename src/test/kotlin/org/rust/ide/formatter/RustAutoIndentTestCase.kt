package org.rust.ide.formatter

import org.rust.lang.RustTestCaseBase

class RustAutoIndentTestCase : RustTestCaseBase() {
    override val dataPath = "org/rust/ide/formatter/fixtures/auto_indent"

    fun testFn() = doTest()
    fun testIf() = doTest()
    fun testModItem() = doTest()
    fun testModItem2() = doTest()
    fun testForeignModItem() = doTest()
    fun testForeignModItem2() = doTest()
    fun testPat() = doTest()
    fun testPat2() = doTest()

    private fun doTest() = checkByFile {
        myFixture.type('\n')
    }
}
