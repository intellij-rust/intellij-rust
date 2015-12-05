package org.rust.lang.annotator

import org.rust.lang.RustTestCase

class RustAnnotatorTest : RustTestCase() {
    override fun getTestDataPath() = "src/test/resources/org/rust/lang/annotator/fixtures"

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
