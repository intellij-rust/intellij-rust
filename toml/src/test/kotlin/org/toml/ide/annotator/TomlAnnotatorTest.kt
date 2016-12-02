package org.toml.ide.annotator

import org.toml.TomlTestCaseBase

class TomlAnnotatorTest : TomlTestCaseBase() {
    override fun getTestDataPath() = "src/test/resources/org/toml/ide/annotator/fixtures"
    private fun doTestInfo() = myFixture.testHighlighting(true, true, true, fileName)

    fun testInlineTables() {
        doTestInfo()
    }
}
