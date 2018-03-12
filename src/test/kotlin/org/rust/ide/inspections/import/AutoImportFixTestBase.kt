/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import org.intellij.lang.annotations.Language
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.openapiext.Testmark

abstract class AutoImportFixTestBase : RsInspectionsTestBase(RsUnresolvedReferenceInspection()) {

    protected fun checkAutoImportFixIsUnavailable(@Language("Rust") text: String, testmark: Testmark? = null) =
        doTest { checkFixIsUnavailable(AutoImportFix.NAME, text, testmark = testmark) }

    protected fun checkAutoImportFixByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        testmark: Testmark? = null
    ) = doTest { checkFixByText(AutoImportFix.NAME, before, after, testmark = testmark) }

    protected fun checkAutoImportFixByFileTree(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        testmark: Testmark? = null
    ) = doTest { checkFixByFileTree(AutoImportFix.NAME, before, after, testmark = testmark) }

    protected fun checkAutoImportFixByTextWithMultipleChoice(
        @Language("Rust") before: String,
        expectedElements: Set<String>,
        choice: String,
        @Language("Rust") after: String
    ) = doTest {
        var chooseItemWasCalled = false

        withMockImportItemUi(object : ImportItemUi {
            override fun chooseItem(items: List<ImportCandidate>, callback: (ImportCandidate) -> Unit) {
                chooseItemWasCalled = true
                val actualItems = items.mapTo(HashSet()) { it.info.usePath }
                assertEquals(expectedElements, actualItems)
                val selectedValue = items.find { it.info.usePath == choice }
                    ?: error("Can't find `$choice` in `$actualItems`")
                callback(selectedValue)
            }
        }) { checkFixByText(AutoImportFix.NAME, before, after) }

        check(chooseItemWasCalled) { "`chooseItem` was not called" }
    }

    private inline fun doTest(action: () -> Unit) {
        val defaultValue = (inspection as RsUnresolvedReferenceInspection).ignoreWithoutQuickFix
        try {
            inspection.ignoreWithoutQuickFix = false
            action()
        } finally {
            inspection.ignoreWithoutQuickFix = defaultValue
        }
    }
}
