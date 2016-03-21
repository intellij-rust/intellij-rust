package org.rust.ide.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.openapi.fileEditor.FileEditorManager
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase

class RustInspectionsTest : RustTestCaseBase() {

    override val dataPath = "org/rust/ide/inspections/fixtures"

    fun testApproxConstant() = doTest<ApproxConstantInspection>()
    fun testSelfConvention() = doTest<SelfConventionInspection>()

    fun testUnresolvedModuleDeclaration() {
        enableInspection<UnresolvedModuleDeclarationInspection>()
        myFixture.testHighlighting(false, false, false,
            "unresolved_module_declaration/mod.rs")

        applyQuickFix("Create module file")

        val openFiles = FileEditorManager.getInstance(project).openFiles
        assertThat(openFiles.find { it.name == "foo.rs" })
            .isNotNull()
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
