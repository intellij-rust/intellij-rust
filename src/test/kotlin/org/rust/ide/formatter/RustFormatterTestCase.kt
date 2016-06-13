package org.rust.ide.formatter

import com.intellij.psi.formatter.FormatterTestCase
import org.rust.lang.RustLanguage
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
    fun testChainCallAlignment() {
        getSettings(RustLanguage).ALIGN_MULTILINE_CHAINED_METHODS = true
        doTest()
    }
    fun testChainCallAlignmentOff() = doTest()
    fun testChainCallIndent() = doTest()

    // FIXME: these two guys are way too big
    fun testSpacing() = doTest()
    fun testLineBreaks() = doTest()

    fun testIssue451() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/451
}
