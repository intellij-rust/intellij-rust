/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.RsTestBase

abstract class RsInspectionsTestBase(
    val inspection: RsLocalInspectionTool,
    val useStdLib: Boolean = false
) : RsTestBase() {

    fun testInspectionHasDocumentation() {
        val description = "inspectionDescriptions/${inspection.javaClass.simpleName?.dropLast("Inspection".length)}.html"
        val text = getResourceAsString(description)
            ?: error("No inspection description for ${inspection.javaClass} ($description)")
        checkHtmlStyle(text)
    }

    override fun getProjectDescriptor() = if (useStdLib) WithStdlibRustProjectDescriptor else super.getProjectDescriptor()

    protected fun enableInspection() =
        myFixture.enableInspections(inspection.javaClass)

    protected fun doTest() {
        enableInspection()
        myFixture.testHighlighting(true, false, true, fileName)
    }

    protected fun checkByText(
        @Language("Rust") text: String,
        checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false
    ) = check(text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByText)

    protected fun checkFixByText(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false
    ) = checkFix(fixName, before, after,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByText)

    protected fun checkByFileTree(
        @Language("Rust") text: String,
        checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false
    ) = check(text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByFileTree)

    protected fun checkFixByFileTree(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") openedFileAfter: String,
        checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false
    ) = checkFix(fixName, before, openedFileAfter,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByFileTree)

    protected fun checkFixIsUnavailable(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false
    ) {
        checkByText(text, checkWarn, checkInfo, checkWeakWarn)
        TestCase.assertTrue("Fix $fixName should not be possible to apply.",
            myFixture.filterAvailableIntentions(fixName).isEmpty())
    }

    private fun check(
        @Language("Rust") text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        configure: (String) -> Unit
    ) {
        configure(text)
        enableInspection()
        myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
    }

    private fun checkFix(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        configure: (String) -> Unit
    ) {
        check(before, checkWarn, checkInfo, checkWeakWarn, configure)
        applyQuickFix(fixName)
        myFixture.checkResult(replaceCaretMarker(after.trimIndent()))
    }

    private fun configureByText(text: String) {
        InlineFile(text.trimIndent())
    }

    private fun configureByFileTree(text: String) {
        fileTreeFromText(text).createAndOpenFileWithCaretMarker()
    }
}
