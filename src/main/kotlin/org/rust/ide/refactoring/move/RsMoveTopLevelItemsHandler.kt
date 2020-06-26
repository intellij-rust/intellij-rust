/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.descendantOfTypeStrict

class RsMoveTopLevelItemsHandler : MoveHandlerDelegate() {

    override fun supportsLanguage(language: Language): Boolean = language.`is`(RsLanguage)

    override fun canMove(
        element: Array<out PsiElement>,
        targetContainer: PsiElement?,
        reference: PsiReference?
    ): Boolean {
        val containingMod = (element.firstOrNull() as? RsElement)?.containingMod ?: return false
        return element.all { canMoveElement(it) && it.parent == containingMod }
    }

    // looks like IntelliJ platform never calls this method (and always calls `tryToMove` instead)
    // but lets implement it just in case
    override fun doMove(
        project: Project,
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        moveCallback: MoveCallback?
    ) = doMove(project, elements)

    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?
    ): Boolean {
        val elements = arrayOf(element)
        // if something is selected, then most likely user wants to move selected items
        // if cursor is at empty line, then we assume that user wants to move entire file
        // otherwise we assume that user wants to move item under cursor
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        if (hasSelection || canMove(elements, null, reference)) {
            doMove(project, elements)
            return true
        }
        return false
    }

    private fun doMove(project: Project, elements: Array<out PsiElement>) {
        val containingMod = PsiTreeUtil.findCommonParent(*elements)?.parentOfType<RsMod>() ?: return
        val itemsToMove = elements.filterIsInstance<RsItemElement>().toSet()

        if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, itemsToMove, true)) return

        RsMoveTopLevelItemsDialog(project, itemsToMove, containingMod).show()
    }

    companion object {
        fun canMoveElement(element: PsiElement): Boolean {
            if (element is RsModItem && element.descendantOfTypeStrict<RsModDeclItem>() != null) return false
            return element is RsItemElement
                && element !is RsModDeclItem
                && element !is RsUseItem
                && element !is RsExternCrateItem
                && element !is RsForeignModItem
        }
    }
}
