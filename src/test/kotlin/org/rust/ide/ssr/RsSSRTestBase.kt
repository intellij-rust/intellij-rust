/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.ssr

import com.intellij.structuralsearch.inspection.SSBasedInspection
import com.intellij.structuralsearch.inspection.StructuralSearchProfileActionProvider
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration
import org.intellij.lang.annotations.Language
import org.junit.runner.RunWith
import org.rust.RsJUnit4TestRunner
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.lang.RsFileType
import org.rust.openapiext.runWithEnabledFeatures

@RunWith(RsJUnit4TestRunner::class)
abstract class RsSSRTestBase : RsInspectionsTestBase(SSBasedInspection::class) {
    protected fun doTest(@Language("Rust") code: String, pattern: String) {
        runWithEnabledFeatures(RsExperiments.SSR) {
            val configuration = SearchConfiguration().apply {
                matchOptions.setFileType(RsFileType)
                matchOptions.fillSearchCriteria(pattern.trimIndent())
            }
            StructuralSearchProfileActionProvider.createNewInspection(configuration, project)
            checkWarnings(code)
        }
    }
}
