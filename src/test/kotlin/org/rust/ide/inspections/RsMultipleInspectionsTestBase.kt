/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ide.annotator.RsAnnotationTestBase

abstract class RsMultipleInspectionsTestBase(
    vararg val inspections: RsLocalInspectionTool
) : RsAnnotationTestBase() {

    private fun enableInspections() = myFixture.enableInspections(*inspections)

    override fun configureByText(text: String) {
        super.configureByText(text)
        enableInspections()
    }

    override fun configureByFileTree(text: String) {
        super.configureByFileTree(text)
        enableInspections()
    }
}
