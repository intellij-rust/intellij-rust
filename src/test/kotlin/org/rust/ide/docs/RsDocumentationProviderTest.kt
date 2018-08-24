/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.openapiext.Testmark

abstract class RsDocumentationProviderTest : RsTestBase() {

    protected fun doTest(
        @Language("Rust") code: String,
        @Language("Html") expected: String,
        block: RsDocumentationProvider.(PsiElement, PsiElement?) -> String?
    ) {
        InlineFile(code)

        val (originalElement, _, offset) = findElementWithDataAndOffsetInEditor<PsiElement>()
        val element = DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, offset, myFixture.file, originalElement)!!

        val actual = RsDocumentationProvider().block(element, originalElement)?.trim()
        assertSameLines(expected.trimIndent(), actual)
    }

    protected fun doUrlTestByText(@Language("Rust") text: String, expectedUrl: String?, testmark: Testmark? = null) =
        doUrlTest(text, expectedUrl, testmark, this::configureByText)

    protected fun doUrlTestByFileTree(@Language("Rust") text: String, expectedUrl: String?, testmark: Testmark? = null) =
        doUrlTest(text, expectedUrl, testmark, this::configureByFileTree)

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
}
