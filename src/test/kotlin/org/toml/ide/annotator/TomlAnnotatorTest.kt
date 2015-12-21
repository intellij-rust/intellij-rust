package org.toml.ide.annotator

import org.toml.TomlTestCase

class TomlAnnotatorTest : TomlTestCase() {
    override fun getTestDataPath() = "src/test/resources/org/toml/ide/annotator/fixtures"
    private fun doTestInfo() = myFixture.testHighlighting(true, true, true, fileName)

    fun testInlineTables() {
        doTestInfo()
    }
}
