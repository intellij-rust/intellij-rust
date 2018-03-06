/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import junit.framework.TestCase
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.RsTestBase
import org.rust.openapiext.Testmark

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

    private fun enableInspection() = myFixture.enableInspections(inspection)

    protected fun checkByText(
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = check(text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByText,
        testmark = testmark)

    protected fun checkFixByText(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByText,
        checkAfter = this::checkByText,
        testmark = testmark)

    protected fun checkByFileTree(
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = check(text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByFileTree,
        testmark = testmark)

    protected fun checkFixByFileTree(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFix(fixName, before, after,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByFileTree,
        checkAfter = this::checkByFileTree,
        testmark = testmark)

    protected fun checkFixIsUnavailable(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFixIsUnavailable(fixName, text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByText,
        testmark = testmark)

    protected fun checkFixIsUnavailableByFileTree(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        testmark: Testmark? = null
    ) = checkFixIsUnavailable(fixName, text,
        checkWarn = checkWarn,
        checkInfo = checkInfo,
        checkWeakWarn = checkWeakWarn,
        configure = this::configureByFileTree,
        testmark = testmark)

    private fun check(
        @Language("Rust") text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        configure: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        val action: () -> Unit = {
            configure(text)
            enableInspection()
            myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
        }
        testmark?.checkHit(action) ?: action()
    }

    private fun checkFix(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        configure: (String) -> Unit,
        checkAfter: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        val action: () -> Unit = {
            check(before, checkWarn, checkInfo, checkWeakWarn, configure)
            applyQuickFix(fixName)
            checkAfter(after)
        }
        testmark?.checkHit(action) ?: action()
    }

    private fun checkFixIsUnavailable(
        fixName: String,
        @Language("Rust") text: String,
        checkWarn: Boolean,
        checkInfo: Boolean,
        checkWeakWarn: Boolean,
        configure: (String) -> Unit,
        testmark: Testmark? = null
    ) {
        check(text, checkWarn, checkInfo, checkWeakWarn, configure, testmark)
        TestCase.assertTrue("Fix $fixName should not be possible to apply.",
            myFixture.filterAvailableIntentions(fixName).isEmpty())
    }

    private fun configureByText(text: String) {
        InlineFile(text.trimIndent())
    }

    private fun configureByFileTree(text: String) {
        fileTreeFromText(text).createAndOpenFileWithCaretMarker()
    }

    private fun checkByText(text: String) {
        myFixture.checkResult(replaceCaretMarker(text.trimIndent()))
    }

    private fun checkByFileTree(text: String) {
        fileTreeFromText(replaceCaretMarker(text)).check(myFixture)
    }
}
