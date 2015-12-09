package org.rust.lang.annotator

import org.rust.lang.RustTestCaseBase

class RustAnnotatorTest : RustTestCaseBase() {

    override val dataPath = "org/rust/lang/annotator/fixtures"

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
