package org.rust.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import org.rust.lang.RustTestCaseBase

class RustInspectionsTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/inspections/fixtures"

    private inline  fun <reified T: LocalInspectionTool>doTest() {
        myFixture.enableInspections(T::class.java)
        myFixture.testHighlighting(true, false, true, fileName)
    }

    fun testApproxConstant() = doTest<ApproxConstantInspection>()
    fun testSelfConvention() = doTest<SelfConventionInspection>()
}
