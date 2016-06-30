package org.rust.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import org.rust.lang.RustTestCaseBase

class RustInspectionsTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/inspections/fixtures"

    fun testApproxConstant() = doTest<RustApproxConstantInspection>()
    fun testSelfConvention() = doTest<RustSelfConventionInspection>()
    fun testUnresolvedModuleDeclaration() = doTest<RustUnresolvedModuleDeclarationInspection>()

    fun testUnresolvedModuleDeclarationQuickFix() = checkByDirectory {
        enableInspection<RustUnresolvedModuleDeclarationInspection>()
        openFileInEditor("mod.rs")
        applyQuickFix("Create module file")
    }

    fun testUnresolvedLocalModuleDeclaration() = doTest<RustUnresolvedModuleDeclarationInspection>()

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
