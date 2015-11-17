package org.rust.lang.inspections

import com.intellij.codeInspection.LocalInspectionTool
import org.rust.lang.RustTestCase

class RustInspectionsTest : RustTestCase() {
    val NONEXISTENT_MODULE_DECLARATION_HINT = "Create module file"

    override fun getTestDataPath() = "testData/org/rust/lang/inspections/fixtures"

    private inline  fun <reified T: LocalInspectionTool>doTest() {
        myFixture.enableInspections(T::class.java)
        myFixture.testHighlighting(true, false, true, fileName)

    }

    private inline  fun <reified T: LocalInspectionTool>doTestWithIntention(hint: String) {
        doTest<T>()
        myFixture.findSingleIntention(hint)
    }

    fun testApproxConstant() = doTest<ApproxConstantInspection>()
    fun testSelfConvention() = doTest<SelfConventionInspection>()
    fun testNonexistentModuleDeclaration() =
        doTestWithIntention<NonexistentModuleDeclarationInspection>(NONEXISTENT_MODULE_DECLARATION_HINT)
}
