package org.rust.ide.formatter

import com.intellij.psi.formatter.FormatterTestCase
import org.rust.ide.formatter.settings.RustCodeStyleSettings
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
    fun testChainCallAlignmentOff() = doTest()
    fun testChainCallIndent() = doTest()
    fun testLineBreaksInBlocks() = doTest()
    fun testLineBreaksInBlocks2() = doTest()

    fun testChainCallAlignment() {
        common().ALIGN_MULTILINE_CHAINED_METHODS = true
        doTest()
    }

    fun testAlignParamsOff() {
        common().ALIGN_MULTILINE_PARAMETERS = false
        doTest()
    }

    fun testAlignParamsInCallsOff() {
        common().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = false
        doTest()
    }

    fun testAlignRetWhereOff() {
        custom().ALIGN_RET_TYPE_AND_WHERE_CLAUSE = false
        doTest()
    }

    fun testAlignWhereBoundsOff() {
        custom().ALIGN_WHERE_BOUNDS = false
        doTest()
    }

    fun testAlignTypeParamsOn() {
        custom().ALIGN_TYPE_PARAMS = true
        doTest()
    }

    fun testMinNumberOfBlankLines() {
        custom().MIN_NUMBER_OF_BLANKS_BETWEEN_ITEMS = 2
        doTest()
    }

    fun testAllowOneLineMatchOff() = doTest()
    fun testAllowOneLineMatch() {
        custom().ALLOW_ONE_LINE_MATCH = true
        doTest()
    }

    // FIXME: these two guys are way too big
    fun testSpacing() = doTest()
    fun testLineBreaks() = doTest()

    fun testIssue451() = doTest()   // https://github.com/intellij-rust/intellij-rust/issues/451

    private fun common() = getSettings(RustLanguage)
    private fun custom() = settings.getCustomSettings(RustCodeStyleSettings::class.java)
}
