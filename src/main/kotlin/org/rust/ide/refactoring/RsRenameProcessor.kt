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
import org.rust.lang.core.psi.impl.RsModItemImpl

class RsRenameProcessor : RenamePsiElementProcessor() {

    override fun canProcessElement(element: PsiElement): Boolean = element is RsNamedElement

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
        /*if (element is RsFile && element.name == "mod.rs" && element.parent != null)
        {
            super.prepareRenaming(element.parent!!, newName, allRenames)
            return
        }*/

        // If a mod without a reference is renamed, try to rename a directory with the same name
        if (element is RsModItemImpl)
        {
            val modDirectory = element.mod.containingFile.containingDirectory
            element.name?.let {
                modDirectory.findSubdirectory(it)?.let { dir ->
                    allRenames.put(dir, newName)
                }
            }
        }

        super.prepareRenaming(element, newName, allRenames)
        if (element !is RsAbstractable) return
        val trait = (element.owner as? RsAbstractableOwner.Trait)?.trait ?: return
        trait.searchForImplementations()
            .mapNotNull { it.findCorrespondingElement(element) }
            .forEach { allRenames[it] = newName }
    }

    private fun String.ensureQuote(): String = if (startsWith('\'')) this else "'$this"
}
