package org.rust.lang.formatter

import com.intellij.psi.formatter.FormatterTestCase

class RustFormatterTestCase : FormatterTestCase() {
    override fun getTestDataPath() = "testData"

    override fun getBasePath() = "formatter"

    override fun getFileExtension() = "rs"

    fun testBlocks() = doTest()
    fun testItems() = doTest()
    fun testExpressions() = doTest()
}
