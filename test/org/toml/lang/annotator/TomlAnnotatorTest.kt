package org.toml.lang.annotator

import org.toml.lang.TomlTestCase

class TomlAnnotatorTest : TomlTestCase() {
    override fun getTestDataPath() = "testData/toml/annotator/"
    private fun doTestInfo() = myFixture.testHighlighting(true, true, true, fileName)

    fun testInlineTables() {
        doTestInfo()
    }
}
