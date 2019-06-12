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

abstract class RsMultipleInspectionsTestBase(
    private vararg val inspectionClasses: KClass<out InspectionProfileEntry>
) : RsAnnotationTestBase() {

    protected lateinit var inspections: List<InspectionProfileEntry>

    override fun setUp() {
        super.setUp()
        inspections = InspectionTestUtil.instantiateTools(inspectionClasses.map { it.java })
    }

    private fun enableInspections() = myFixture.enableInspections(*inspections.toTypedArray())

    override fun configureByText(text: String) {
        super.configureByText(text)
        enableInspections()
    }

    override fun configureByFileTree(text: String): TestProject {
        val testProject = super.configureByFileTree(text)
        enableInspections()
        return testProject
    }
}
