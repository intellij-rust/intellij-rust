package org.rust.ide.formatter

import com.intellij.psi.formatter.FormatterTestCase
import org.rust.lang.RustTestCaseBase

class RustFormatterTestCase : FormatterTestCase() {
    override fun getTestDataPath() = "src/test/resources"

    override fun getBasePath() = "org/rust/ide/formatter/fixtures"

    override fun getFileExtension() = "rs"

    override fun getTestName(lowercaseFirstLetter: Boolean): String {
        val camelCase = super.getTestName(lowercaseFirstLetter)
        return RustTestCaseBase.camelToSnake(camelCase)
    }

    fun testBlocks() = doTest()
    fun testItems() = doTest()
    fun testExpressions() = doTest()
    fun testArgumentAlignment() = doTest()
    fun testArgumentIndent() = doTest()
    fun testTraits() = doTest()
    fun testTupleAlignment() = doTest()
    fun testChainCallAlignment() = doTest()
    fun testChainCallIndent() = doTest()

    // FIXME: these two guys are way too big
    fun testSpacing() = doTest()
    fun testLineBreaks() = doTest()
}
