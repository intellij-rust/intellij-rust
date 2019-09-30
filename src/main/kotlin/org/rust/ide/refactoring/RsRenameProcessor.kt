/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pass
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameDialog
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import com.intellij.usageView.UsageInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class RsRenameProcessor : RenamePsiElementProcessor() {

    override fun createRenameDialog(project: Project, element: PsiElement, nameSuggestionContext: PsiElement?, editor: Editor?): RenameDialog {
        return object : RenameDialog(project, element, nameSuggestionContext, editor) {
            override fun getFullName(): String {
                val mod = (element as? RsFile)?.modName ?: return super.getFullName()
                return "module $mod"
            }
        }
    }

    override fun canProcessElement(element: PsiElement): Boolean = element is RsNamedElement

    override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
        if (element is RsPatBinding) {
            usages
                .filter {
                    val usageElement = it.element
                    usageElement is RsStructLiteralField && usageElement.colon == null
                }
                .forEach {
                    val newPatField = RsPsiFactory(element.project)
                        .createStructLiteralField(element.text, newName)
                    it.element!!.replace(newPatField)
                }
        }

        val newRenameElement = if (element is RsPatBinding && element.parent.parent is RsPatStruct) {
            val newPatField = RsPsiFactory(element.project)
                .createPatFieldFull(element.identifier.text, element.text)
            element.replace(newPatField).descendantOfTypeStrict<RsPatBinding>()!!
        } else element
        super.renameElement(newRenameElement, newName, usages, listener)
    }

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
        when (element) {
            is RsAbstractable -> {
                val trait = (element.owner as? RsAbstractableOwner.Trait)?.trait ?: return
                trait.searchForImplementations()
                    .mapNotNull { it.findCorrespondingElement(element) }
                    .forEach { allRenames[it] = newName }
            }
            is RsMod -> {
                if (element is RsFile && element.declaration == null) return

                val ownedDir = element.getOwnedDirectory() ?: return
                allRenames[ownedDir] = newName
            }
        }

    }

    private fun String.ensureQuote(): String = if (startsWith('\'')) this else "'$this"
}
