/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.rust.RsJUnit4ParameterizedTestRunner
import org.rust.SkipTestWrapping
import org.rust.TestProject
import org.rust.TestWrapping
import org.rust.ide.annotator.RsAnnotationTestBase
import org.rust.ide.annotator.RsAnnotationTestFixture
import kotlin.reflect.KClass

/**
 * A base test class for [inspection][RsLocalInspectionTool] tests.
 *
 * By default, each test declared in a subclass of this class will run several times - one per each
 * [TestWrapping] value returned from [RsInspectionsTestBase.data] method. This allows us to test inspections
 * under different circumstances, e.g. inside a procedural macro call. Use [SkipTestWrapping]
 * annotation to skip test run with a specific (or all) [TestWrapping] (s).
 */
@RunWith(RsJUnit4ParameterizedTestRunner::class)
@Parameterized.UseParametersRunnerFactory(RsJUnit4ParameterizedTestRunner.RsRunnerForParameters.Factory::class)
abstract class RsInspectionsTestBase(
    protected val inspectionClass: KClass<out InspectionProfileEntry>
) : RsAnnotationTestBase() {

    @JvmField
    @field:Parameterized.Parameter(0)
    var testWrappingField: TestWrapping = TestWrapping.NONE

    override val testWrapping: TestWrapping get() = testWrappingField

    companion object {
        @Parameterized.Parameters(name = "{index}: {0}")
        @JvmStatic fun data(): Iterable<TestWrapping> = listOf(
            TestWrapping.NONE,
            TestWrapping.ATTR_MACRO_AS_IS_ALL_ITEMS,
        )
    }

    override fun createAnnotationFixture(): RsAnnotationTestFixture<Unit> =
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
