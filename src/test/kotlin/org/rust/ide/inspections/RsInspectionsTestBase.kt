/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.rust.TestProject
import org.rust.ide.annotator.RsAnnotationTestBase
import org.rust.ide.annotator.RsAnnotationTestFixture
import kotlin.reflect.KClass

abstract class RsInspectionsTestBase(
    protected val inspectionClass: KClass<out InspectionProfileEntry>
) : RsAnnotationTestBase() {

    override fun createAnnotationFixture(): RsAnnotationTestFixture =
        RsAnnotationTestFixture(this, myFixture, inspectionClasses = listOf(inspectionClass))

    protected lateinit var inspection: InspectionProfileEntry

    override fun setUp() {
        super.setUp()
        inspection = annotationFixture.enabledInspections[0]
    }

    fun testInspectionHasDocumentation() {
        if (inspection is RsLocalInspectionTool) {
            val description = "inspectionDescriptions/${inspection.javaClass.simpleName.dropLast("Inspection".length)}.html"
            val text = getResourceAsString(description)
                ?: error("No inspection description for ${inspection.javaClass} ($description)")
            checkHtmlStyle(text)
        }
    }

    private fun enableInspection() = myFixture.enableInspections(inspection)

    override fun configureByText(text: String) {
        super.configureByText(text)
        enableInspection()
    }

    override fun configureByFileTree(text: String): TestProject {
        val testProject = super.configureByFileTree(text)
        enableInspection()
        return testProject
    }
}
