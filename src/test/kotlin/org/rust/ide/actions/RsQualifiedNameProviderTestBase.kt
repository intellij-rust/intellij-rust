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
        references : List<String?>
    ) {
        InlineFile(code)
        qualifiedNamesForDeclarations().forEach { println(it) }
        assertEquals(references, qualifiedNamesForDeclarations())
    }

    private fun qualifiedNamesForDeclarations(): List<String?> {
        val result = ArrayList<String?>()
        myFixture.file.accept(object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element as? RsQualifiedNamedElement != null) {
                    result.add(CopyReferenceAction.elementToFqn(element))
                }
                element.acceptChildren(this)
            }
        })
        return result
    }
}
