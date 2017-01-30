package org.rust.ide.inspections

import org.rust.lang.RsTestBase

abstract class RsInspectionsTestBase(
    val inspection: RsLocalInspectionTool,
    val useStdLib: Boolean = false
) : RsTestBase() {

    override val dataPath = ""

    fun testInspectionHasDocumentation() {
        val description = "inspectionDescriptions/${inspection.javaClass.simpleName?.dropLast("Inspection".length)}.html"
        inspection.javaClass.classLoader.getResource(description)
            ?: error("No inspection description for ${inspection.javaClass} ($description)")
    }

    override fun getProjectDescriptor() = if (useStdLib) WithStdlibRustProjectDescriptor else super.getProjectDescriptor()

    protected fun enableInspection() =
        myFixture.enableInspections(inspection.javaClass)

    protected fun doTest() {
        enableInspection()
        myFixture.testHighlighting(true, false, true, fileName)
    }

    protected fun checkByText(
        text: String,
        checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false
    ) {
        myFixture.configureByText("main.rs", text)
        enableInspection()
        myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
    }

    protected fun checkFixByText(fixName: String, before: String, after: String, checkWarn: Boolean = true, checkInfo: Boolean = false, checkWeakWarn: Boolean = false) {
        myFixture.configureByText("main.rs", before)
        enableInspection()
        myFixture.checkHighlighting(checkWarn, checkInfo, checkWeakWarn)
        applyQuickFix(fixName)
        myFixture.checkResult(after)
    }

}
