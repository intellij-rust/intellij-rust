package org.rust.ide.annotator

import org.rust.lang.RustTestCaseBase

class RustAnnotatorTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/annotator/fixtures"

    private fun doTestInfo() {
        myFixture.testHighlighting(false, true, false, fileName)
    }

    fun testAttributes() {
        doTestInfo()
    }

    fun testMacro() {
        doTestInfo()
    }

    fun testTypeParameters() {
        doTestInfo()
    }

    fun testMutBinding() {
        doTestInfo()
    }
}
