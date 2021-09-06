/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import org.intellij.lang.annotations.Language
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsUnresolvedReferenceInspection
import org.rust.ide.utils.import.ImportCandidate
import org.rust.openapiext.Testmark
import org.rust.lang.core.psi.RsFile

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
        @Language("Rust") after: String
    ) = doTest {
        var chooseItemWasCalled = false

        withMockImportItemUi(object : ImportItemUi {
            override fun chooseItem(items: List<ImportCandidate>, callback: (ImportCandidate) -> Unit) {
                chooseItemWasCalled = true
                val actualItems = items.map { it.info.usePath }
                assertEquals(expectedElements, actualItems)
                val selectedValue = items.find { it.info.usePath == choice }
                    ?: error("Can't find `$choice` in `$actualItems`")
                callback(selectedValue)
            }
        }) { checkFixByText(AutoImportFix.NAME, before, after) }

        check(chooseItemWasCalled) { "`chooseItem` was not called" }
    }

    protected fun checkAutoImportFixByFileTreeWithMultipleChoice(
        @Language("Rust") before: String,
        expectedElements: List<String>,
        choice: String,
        @Language("Rust") after: String,
    ) = doTest {
        var chooseItemWasCalled = false

        withMockImportItemUi(object : ImportItemUi {
            override fun chooseItem(items: List<ImportCandidate>, callback: (ImportCandidate) -> Unit) {
                chooseItemWasCalled = true
                val actualItems = items.map { it.info.usePath }
                assertEquals(expectedElements, actualItems)
                val selectedValue = items.find { it.info.usePath == choice }
                    ?: error("Can't find `$choice` in `$actualItems`")
                callback(selectedValue)
            }
        }) { checkFixByFileTree(AutoImportFix.NAME, before, after) }

        check(chooseItemWasCalled) { "`chooseItem` was not called" }
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
