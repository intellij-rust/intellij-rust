package org.rust.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import org.rust.lang.RustTestCaseBase

class RustInspectionsTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/inspections/fixtures"

    fun testApproxConstant() = doTest<ApproxConstantInspection>()
    fun testSelfConvention() = doTest<SelfConventionInspection>()
    fun testUnresolvedModuleDeclaration() = doTest<UnresolvedModuleDeclarationInspection>()

    fun testUnresolvedModuleDeclarationQuickFix() = checkByDirectory {
        enableInspection<UnresolvedModuleDeclarationInspection>()
        openFileInEditor("mod.rs")
        applyQuickFix("Create module file")
    }

    fun testUnresolvedLocalModuleDeclaration() = doTest<UnresolvedModuleDeclarationInspection>()

    private inline fun<reified T: LocalInspectionTool>enableInspection() {
        myFixture.enableInspections(T::class.java)
    }

    private inline  fun <reified T: LocalInspectionTool>doTest() {
        enableInspection<T>()
        myFixture.testHighlighting(true, false, true, fileName)
    }

    private fun applyQuickFix(name: String) {
        val action = myFixture.getAvailableIntention(name)!!
        myFixture.launchAction(action)
    }
}
