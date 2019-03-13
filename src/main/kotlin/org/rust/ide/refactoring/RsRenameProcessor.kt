/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean = element !is RsTupleFieldDecl

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
        if (element is RsLifetime || element is RsLifetimeParameter || element is RsLabel || element is RsLabelDecl) {
            allRenames.put(element, newName.ensureQuote())
        } else {
            allRenames.put(element, newName.trimStart('\''))
        }
    }

    override fun substituteElementToRename(element: PsiElement, editor: Editor?): PsiElement =
        (element as? RsAbstractable)?.superItem ?: element

    override fun substituteElementToRename(element: PsiElement, editor: Editor, renameCallback: Pass<PsiElement>) =
        renameCallback.pass(substituteElementToRename(element, editor))

    override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>) {
        super.prepareRenaming(element, newName, allRenames)
        if (element !is RsAbstractable) return
        val trait = (element.owner as? RsAbstractableOwner.Trait)?.trait ?: return
        trait.searchForImplementations()
            .mapNotNull { it.findCorrespondingElement(element) }
            .forEach { allRenames[it] = newName }
    }

    private fun String.ensureQuote(): String = if (startsWith('\'')) this else "'$this"
}
