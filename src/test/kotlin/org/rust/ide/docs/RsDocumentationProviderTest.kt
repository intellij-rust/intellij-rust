/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

abstract class RsDocumentationProviderTest : RsTestBase() {

    protected fun doTest(
        @Language("Rust") code: String,
        @Language("Html") expected: String?,
        findElement: () -> Pair<PsiElement, Int> = { findElementAndOffsetInEditor() },
        block: RsDocumentationProvider.(PsiElement, PsiElement?) -> String?
    ) {
        @Suppress("NAME_SHADOWING")
        doTest(code, expected, findElement, block) { actual, expected ->
            assertSameLines(expected.trimIndent(), actual)
        }
    }

    protected fun doTest(
        @Language("Rust") code: String,
        expected: Regex?,
        findElement: () -> Pair<PsiElement, Int> = { findElementAndOffsetInEditor() },
        block: RsDocumentationProvider.(PsiElement, PsiElement?) -> String?
    ) {
        @Suppress("NAME_SHADOWING")
        doTest(code, expected, findElement, block) { actual, expected ->
            assertTrue(actual.matches(expected))
        }
    }

    protected fun <T> doTest(
        @Language("Rust") code: String,
        expected: T?,
        findElement: () -> Pair<PsiElement, Int> = { findElementAndOffsetInEditor() },
        block: RsDocumentationProvider.(PsiElement, PsiElement?) -> String?,
        check: (String, T) -> Unit
    ) {
        InlineFile(code)

        val (originalElement, offset) = findElement()
        val element = DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, offset, myFixture.file, originalElement)!!

        val actual = RsDocumentationProvider().block(element, originalElement)?.trim()
        if (expected == null) {
            check(actual == null) { "Expected null, got `$actual`" }
        } else {
            check(actual != null) { "Expected not null result" }
            check(actual, expected)
        }
    }

    protected fun doUrlTestByText(@Language("Rust") text: String, expectedUrl: String?, testmark: Testmark? = null) =
        doUrlTest(text, expectedUrl, testmark, this::configureByText)

    protected fun doUrlTestByFileTree(@Language("Rust") text: String, expectedUrl: String?, testmark: Testmark? = null) =
        doUrlTest(text, expectedUrl, testmark) { configureByFileTree(it) }

    private fun doUrlTest(
        @Language("Rust") text: String,
        expectedUrl: String?,
        testmark: Testmark?,
        configure: (String) -> Unit
    ) {
        configure(text)

        val (originalElement, _, offset) = findElementWithDataAndOffsetInEditor<PsiElement>()
        val element = DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, offset, myFixture.file, originalElement)!!

        val action: () -> Unit = {
            val actualUrls = RsDocumentationProvider().getUrlFor(element, originalElement)
            assertEquals(listOfNotNull(expectedUrl), actualUrls)
        }
        testmark?.checkHit(action) ?: action()
    }

    protected fun String.hideSpecificStyles(): String = replace(STYLE_REGEX, """style="..."""")

    companion object {
        private val STYLE_REGEX = Regex("""style=".*?"""")
    }
}
