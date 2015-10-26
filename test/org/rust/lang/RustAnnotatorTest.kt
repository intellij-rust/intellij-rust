package org.rust.lang

class RustAnnotatorTest : RustTestCase() {
    override fun getTestDataPath() = "testData/annotator/"

    private fun doTestInfo() {
        myFixture.testHighlighting(false, true, false, fileName)
    }

    fun testAttributes() {
        doTestInfo()
    }
}