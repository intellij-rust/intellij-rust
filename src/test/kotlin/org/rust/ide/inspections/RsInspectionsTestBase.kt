/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.intellij.lang.annotations.Language
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
    ) {
        myFixture.configureByText("main.rs", text)
        enableInspection()
        myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
    }

    protected fun checkFixByText(
        fixName: String,
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false
    ) {
        myFixture.configureByText("main.rs", before)
        enableInspection()
        myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
        applyQuickFix(fixName)
        myFixture.checkResult(after)
    }

}
