package org.rust.lang.spellchecker

import com.intellij.spellchecker.inspections.SpellCheckingInspection
import org.rust.lang.RustTestCase

class RustSpellCheckerTest : RustTestCase() {
    override fun getTestDataPath() = "testData/org/rust/lang/spellchecker/fixtures"
    private fun doTest(processComments: Boolean = true, processLiterals: Boolean = true) {
        val inspection = SpellCheckingInspection()
        inspection.processLiterals = processLiterals
        inspection.processComments = processComments

        myFixture.enableInspections(inspection)
        myFixture.testHighlighting(false, false, true, fileName)
    }

    fun testComments() = doTest()
    fun testStringLiterals() = doTest()
    fun testCommentsSuppressed() = doTest(processComments = false)
    fun testStringLiteralsSuppressed() = doTest(processLiterals = false)
}
