/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.structuralsearch.inspection.SSBasedInspection
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.RsFileType

abstract class RsStructuralSearchTestBase : RsTestBase() {
    private var inspection: SSBasedInspection? = null

    override fun setUp() {
        super.setUp()
        inspection = SSBasedInspection()
        myFixture.enableInspections(inspection)
    }

    protected fun doTest(@Language("Rust") code: String, pattern: String) {
        InlineFile(code.trimIndent())

        val configuration = SearchConfiguration().apply {
            matchOptions.setFileType(RsFileType)
            matchOptions.fillSearchCriteria(pattern.trimIndent())
        }

        StructuralSearchProfileActionProvider.createNewInspection(configuration, project)
        myFixture.checkHighlighting(true, false, false)
    }
}
