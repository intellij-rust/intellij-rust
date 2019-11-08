/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.rust.ide.annotator.RsAnnotationTestBase
import org.rust.ide.annotator.RsAnnotationTestFixture
import kotlin.reflect.KClass

abstract class RsMultipleInspectionsTestBase(
    private vararg val inspectionClasses: KClass<out InspectionProfileEntry>
) : RsAnnotationTestBase() {

    override fun createAnnotationFixture(): RsAnnotationTestFixture {
        return RsAnnotationTestFixture(myFixture, inspectionClasses = inspectionClasses.toList())
    }
}
