/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import com.intellij.testFramework.InspectionTestUtil
import org.rust.TestProject
import org.rust.ide.annotator.RsAnnotationTestBase
import kotlin.reflect.KClass

abstract class RsInspectionsTestBase(
    private val inspectionClass: KClass<out InspectionProfileEntry>
) : RsAnnotationTestBase() {

    protected lateinit var inspection: InspectionProfileEntry

    override fun setUp() {
        super.setUp()
        inspection = InspectionTestUtil.instantiateTools(listOf(inspectionClass.java))[0]
    }

    fun testInspectionHasDocumentation() {
        if (inspection is RsLocalInspectionTool) {
            val description = "inspectionDescriptions/${inspection.javaClass.simpleName?.dropLast("Inspection".length)}.html"
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
