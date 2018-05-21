/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.ide.actions.CopyReferenceAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.*

abstract class RsQualifiedNameProviderTestBase : RsTestBase() {
    protected fun doTest(
        @Language("Rust") code: String,
        references : Set<String>
    ) {
        InlineFile(code)
        assertEquals(references, qualifiedNamesForDeclarations())
    }

    private fun qualifiedNamesForDeclarations(): Set<String> {
        val result = mutableSetOf<String>()
        myFixture.file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element is RsQualifiedNamedElement) {
                    result.add(CopyReferenceAction.elementToFqn(element) ?: "")
                }
                element.acceptChildren(this)
            }
        })
        return result
    }
}
