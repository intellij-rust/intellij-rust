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

    protected fun applyQuickFix(name: String) {
        val action = myFixture.getAvailableIntention(name)!!
        myFixture.launchAction(action)
    }

}
