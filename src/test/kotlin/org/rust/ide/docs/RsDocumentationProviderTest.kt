/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.codeInsight.documentation.DocumentationManager
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase

abstract class RsDocumentationProviderTest : RsTestBase() {

    protected inline fun doTest(@Language("Rust") code: String,
                                @Language("Html") expected: String,
                                block: RsDocumentationProvider.(PsiElement, PsiElement?) -> String?) {
        InlineFile(code)

        val (originalElement, _, offset) = findElementWithDataAndOffsetInEditor<PsiElement>()
        val element = DocumentationManager.getInstance(project)
            .findTargetElement(myFixture.editor, offset, myFixture.file, originalElement)!!

        val actual = RsDocumentationProvider().block(element, originalElement)?.trim()
        assertSameLines(expected.trimIndent(), actual)
    }
}
