package org.rust.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import org.rust.lang.RustTestCaseBase

abstract class RustInspectionsTestBase : RustTestCaseBase() {
    protected inline fun<reified T : LocalInspectionTool> enableInspection() {
        myFixture.enableInspections(T::class.java)
    }

    protected inline fun <reified T : LocalInspectionTool> doTest() {
        enableInspection<T>()
        myFixture.testHighlighting(true, false, true, fileName)
    }

    protected inline fun <reified T : LocalInspectionTool> checkByText(text: String, checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false) {
        myFixture.configureByText("main.rs", text)
        enableInspection<T>()
        myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
    }

    protected inline fun <reified T: LocalInspectionTool> checkFixByText(fixName: String, before: String, after: String, checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false) {
        myFixture.configureByText("main.rs", before)
        enableInspection<T>()
        myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
        applyQuickFix(fixName)
        myFixture.checkResult(after)
    }

}
