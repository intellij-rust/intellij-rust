/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.structuralsearch.inspection.SSBasedInspection
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration
import org.rust.RsTestBase
import org.rust.lang.RsFileType

abstract class RsSSRTestBase : RsTestBase() {
    private var inspection: SSBasedInspection? = null

    override fun setUp() {
        super.setUp()
        inspection = SSBasedInspection()
        myFixture.enableInspections(inspection)
    }

    protected fun doTest(code: String, pattern: String) {
        InlineFile(code.trimIndent())

        val configuration = SearchConfiguration().apply {
            matchOptions.setFileType(RsFileType)
            matchOptions.fillSearchCriteria(pattern.trimIndent())
        }

        StructuralSearchProfileActionProvider.createNewInspection(configuration, project)
        myFixture.checkHighlighting(true, false, false)
    }
}
