package org.rust.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class RustAnnotatorTest : LightCodeInsightFixtureTestCase() {
    override fun getTestDataPath() = "testData/annotator/"

    private fun doTestInfo() {
        val fileName = getTestName(true) + ".rs";
        myFixture.testHighlighting(false, true, false, fileName)
    }

    fun testAttributes() {
        doTestInfo()
    }
}