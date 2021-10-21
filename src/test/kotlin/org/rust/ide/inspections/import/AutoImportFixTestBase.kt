/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import org.intellij.lang.annotations.Language
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.ide.utils.import.ImportCandidateBase
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.Testmark

abstract class AutoImportFixTestBase : RsInspectionsTestBase(RsUnresolvedReferenceInspection::class) {

    protected fun checkAutoImportFixIsUnavailable(@Language("Rust") text: String, testmark: Testmark? = null) =
        doTest(checkOptimizeImports = false) { checkFixIsUnavailable(AutoImportFix.NAME, text, testmark = testmark) }

    protected fun checkAutoImportFixIsUnavailableByFileTree(@Language("Rust") text: String, testmark: Testmark? = null) =
        doTest(checkOptimizeImports = false) { checkFixIsUnavailableByFileTree(AutoImportFix.NAME, text, testmark = testmark) }

    protected fun checkAutoImportFixByText(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        testmark: Testmark? = null,
        checkOptimizeImports: Boolean = true,
    ) = doTest(checkOptimizeImports) { checkFixByText(AutoImportFix.NAME, before, after, testmark = testmark) }

    protected fun checkAutoImportFixByTextWithoutHighlighting(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = doTest { checkFixByTextWithoutHighlighting(AutoImportFix.NAME, before, after) }

    protected fun checkAutoImportFixByFileTree(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        testmark: Testmark? = null,
        checkOptimizeImports: Boolean = true,
    ) = doTest(checkOptimizeImports) { checkFixByFileTree(AutoImportFix.NAME, before, after, testmark = testmark) }

    protected fun checkAutoImportFixByFileTreeWithoutHighlighting(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        testmark: Testmark? = null
    ) = doTest { checkFixByFileTreeWithoutHighlighting(AutoImportFix.NAME, before, after, testmark = testmark) }

    protected fun checkAutoImportFixByTextWithMultipleChoice(
        @Language("Rust") before: String,
        expectedElements: List<String>,
        choice: String,
        @Language("Rust") after: String,
        testmark: Testmark? = null
    ) = checkAutoImportFixWithMultipleChoice(expectedElements, choice) {
        checkFixByText(AutoImportFix.NAME, before, after, testmark = testmark)
    }

    protected fun checkAutoImportFixByFileTreeWithMultipleChoice(
        @Language("Rust") before: String,
        expectedElements: List<String>,
        choice: String,
        @Language("Rust") after: String,
    ) = checkAutoImportFixWithMultipleChoice(expectedElements, choice) {
        checkFixByFileTree(AutoImportFix.NAME, before, after)
    }

    protected fun checkAutoImportVariantsByText(
        @Language("Rust") before: String,
        expectedElements: List<String>
    ) = checkAutoImportFixWithMultipleChoice(expectedElements, choice = null) {
        configureByText(before)
        annotationFixture.applyQuickFix(AutoImportFix.NAME)
    }

    private fun checkAutoImportFixWithMultipleChoice(
        expectedElements: List<String>,
        choice: String?,
        action: () -> Unit,
    ) = doTest {
        var areElementEquals: Boolean? = null

        withMockImportItemUi(object : ImportItemUi {
            override fun chooseItem(items: List<ImportCandidateBase>, callback: (ImportCandidateBase) -> Unit) {
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
