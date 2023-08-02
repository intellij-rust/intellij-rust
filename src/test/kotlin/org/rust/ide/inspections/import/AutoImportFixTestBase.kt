/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.ide.utils.import.ImportCandidate
import org.rust.lang.core.psi.RsFile

abstract class AutoImportFixTestBase : RsInspectionsTestBase(RsUnresolvedReferenceInspection::class) {

    protected fun checkAutoImportFixIsUnavailable(@Language("Rust") text: String) =
        doTest(checkOptimizeImports = false) { checkFixIsUnavailable(AutoImportFix.NAME, text) }

    protected fun checkAutoImportFixIsUnavailableByFileTree(@Language("Rust") text: String) =
        doTest(checkOptimizeImports = false) { checkFixIsUnavailableByFileTree(AutoImportFix.NAME, text) }

    protected fun checkAutoImportFixByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkOptimizeImports: Boolean = true,
    ) = doTest(checkOptimizeImports) { checkFixByText(AutoImportFix.NAME, before, after, preview = null) }

    protected fun checkAutoImportFixByTextWithoutHighlighting(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = doTest { checkFixByTextWithoutHighlighting(AutoImportFix.NAME, before, after, preview = null) }

    protected fun checkAutoImportFixByFileTree(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        checkOptimizeImports: Boolean = true,
    ) = doTest(checkOptimizeImports) { checkFixByFileTree(AutoImportFix.NAME, before, after, preview = null) }

    protected fun checkAutoImportFixByFileTreeWithoutHighlighting(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
    ) = doTest { checkFixByFileTreeWithoutHighlighting(AutoImportFix.NAME, before, after, preview = null) }

    protected fun checkAutoImportFixByTextWithMultipleChoice(
        @Language("Rust") before: String,
        expectedElements: List<String>,
        choice: String,
        @Language("Rust") after: String,
    ) = doTest {
        checkAutoImportWithMultipleChoice(expectedElements, choice) {
            checkFixByText(AutoImportFix.NAME, before, after, preview = null)
        }
    }

    protected fun checkAutoImportFixByFileTreeWithMultipleChoice(
        @Language("Rust") before: String,
        expectedElements: List<String>,
        choice: String,
        @Language("Rust") after: String,
    ) = doTest {
        checkAutoImportWithMultipleChoice(expectedElements, choice) {
            checkFixByFileTree(AutoImportFix.NAME, before, after, preview = null)
        }
    }

    protected fun checkAutoImportVariantsByText(
        @Language("Rust") before: String,
        expectedElements: List<String>
    ) = doTest {
        checkAutoImportWithMultipleChoice(expectedElements, choice = null) {
            configureByText(before)
            annotationFixture.applyQuickFix(AutoImportFix.NAME, preview = null).checkPreview()
        }
    }

    private inline fun doTest(checkOptimizeImports: Boolean = true, action: () -> Unit) {
        val inspection = inspection as RsUnresolvedReferenceInspection
        val defaultValue = inspection.ignoreWithoutQuickFix
        try {
            inspection.ignoreWithoutQuickFix = false
            action()
            if (checkOptimizeImports) {
                checkNoChangeAfterOptimizeImports()
            }
        } finally {
            inspection.ignoreWithoutQuickFix = defaultValue
        }
    }

    private fun checkNoChangeAfterOptimizeImports() {
        if ((myFixture.file as? RsFile)?.isDoctestInjection == true) return
        val text = myFixture.file.text
        myFixture.performEditorAction("OptimizeImports")
        myFixture.checkResult(text)
    }
}

fun checkAutoImportWithMultipleChoice(expectedElements: List<String>, choice: String?, action: () -> Unit) {
    var areElementEquals: Boolean? = null

    withMockImportItemUi(object : ImportItemUi {
        override fun chooseItem(items: List<ImportCandidate>, callback: (ImportCandidate) -> Unit) {
            val actualItems = items.map { it.info.usePath }
            areElementEquals = expectedElements == actualItems
            assertEquals(expectedElements, actualItems)  // exception here does not fail the test
            if (choice != null) {
                val selectedValue = items.find { it.info.usePath == choice }
                    ?: error("Can't find `$choice` in `$actualItems`")
                callback(selectedValue)
            }
        }
    }, action)

    check(areElementEquals != null) { "`chooseItem` was not called" }
    check(areElementEquals!!)
}
