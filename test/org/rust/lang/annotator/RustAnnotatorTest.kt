package org.rust.lang.annotator

import org.rust.lang.RustTestCase

class RustAnnotatorTest : RustTestCase() {
    override fun getTestDataPath() = "testData/org/rust/lang/annotator/testdata"

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
}
