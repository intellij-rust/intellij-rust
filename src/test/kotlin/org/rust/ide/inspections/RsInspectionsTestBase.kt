/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ide.annotator.RsAnnotationTestBase

abstract class RsInspectionsTestBase(
    val inspection: RsLocalInspectionTool,
    val useStdLib: Boolean = false
) : RsAnnotationTestBase() {

    fun testInspectionHasDocumentation() {
        val description = "inspectionDescriptions/${inspection.javaClass.simpleName?.dropLast("Inspection".length)}.html"
        val text = getResourceAsString(description)
            ?: error("No inspection description for ${inspection.javaClass} ($description)")
        checkHtmlStyle(text)
    }

    override fun getProjectDescriptor() = if (useStdLib) WithStdlibRustProjectDescriptor else super.getProjectDescriptor()

    private fun enableInspection() = myFixture.enableInspections(inspection)

    override fun configureByText(text: String) {
        super.configureByText(text)
        enableInspection()
    }

    override fun configureByFileTree(text: String) {
        super.configureByFileTree(text)
        enableInspection()
    }
}
