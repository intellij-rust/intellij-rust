package org.rust.lang.inspections

import com.intellij.codeInspection.LocalInspectionTool
import org.rust.lang.RustTestCase

class RustInspectionsTest : RustTestCase() {
    override fun getTestDataPath() = "testData/org/rust/lang/inspections/fixtures"

    private inline  fun <reified T: LocalInspectionTool>doTest() {
        myFixture.enableInspections(T::class.java)
        myFixture.testHighlighting(true, false, true, fileName)
    }

    fun testApproxConstant() = doTest<ApproxConstantInspection>()
    fun testSelfConvention() = doTest<SelfConventionInspection>()
}
