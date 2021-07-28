/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.toml.ide.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.toml.ide.annotator.TomlAnnotationTestBase
import org.toml.ide.annotator.TomlAnnotationTestFixture
import kotlin.reflect.KClass

abstract class TomlInspectionsTestBase(
    private val inspectionClass: KClass<out InspectionProfileEntry>
) : TomlAnnotationTestBase() {

    override fun createAnnotationFixture(): TomlAnnotationTestFixture =
        TomlAnnotationTestFixture(this, myFixture, inspectionClasses = listOf(inspectionClass))

    private lateinit var inspection: InspectionProfileEntry

    override fun setUp() {
        super.setUp()
        inspection = annotationFixture.enabledInspections[0]
    }
}
