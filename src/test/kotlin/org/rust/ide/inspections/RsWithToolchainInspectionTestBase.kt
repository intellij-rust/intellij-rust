/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.codeInspection.InspectionProfileEntry
import org.rust.ide.annotator.RsAnnotationTestFixture
import org.rust.ide.annotator.RsWithToolchainAnnotationTestBase
import kotlin.reflect.KClass

abstract class RsWithToolchainInspectionTestBase<C>(
    protected val inspectionClass: KClass<out InspectionProfileEntry>
) : RsWithToolchainAnnotationTestBase<C>() {

    override fun createAnnotationFixture(): RsAnnotationTestFixture<C> =
        RsAnnotationTestFixture(this, myFixture, inspectionClasses = listOf(inspectionClass))
}
