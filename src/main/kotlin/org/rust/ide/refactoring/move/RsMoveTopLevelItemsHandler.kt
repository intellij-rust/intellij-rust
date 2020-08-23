/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.project.Project
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.move.MoveCallback
import com.intellij.refactoring.move.MoveHandlerDelegate
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.rust.ide.utils.collectElements
import org.rust.ide.utils.getElementRange
import org.rust.ide.utils.getTopmostParentInside
import org.rust.lang.RsLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type
import org.rust.openapiext.toPsiFile

class RsMoveTopLevelItemsHandler : MoveHandlerDelegate() {

    override fun supportsLanguage(language: Language): Boolean = language.`is`(RsLanguage)

    override fun canMove(
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        reference: PsiReference?
    ): Boolean {
        val containingMod = (elements.firstOrNull() as? RsElement)?.containingMod ?: return false
        return elements.all { canMoveElement(it) && it.parent == containingMod }
    }

    // Invoked in non-editor context (e.g file structure view)
    override fun doMove(
        project: Project,
        elements: Array<out PsiElement>,
        targetContainer: PsiElement?,
        moveCallback: MoveCallback?
    ) = doMove(project, elements.toList(), null)

    // Invoked in editor context
    override fun tryToMove(
        element: PsiElement,
        project: Project,
        dataContext: DataContext?,
        reference: PsiReference?,
        editor: Editor?
    ): Boolean {
        val hasSelection = editor?.selectionModel?.hasSelection() ?: false
        if (hasSelection || element !is PsiFile) {
            doMove(project, listOf(element), editor)
            return true
        }
        return false
    }

    private fun doMove(project: Project, elements: List<PsiElement>, editor: Editor?) {
        val (itemsToMove, containingMod) = editor?.let { collectInitialItems(project, editor) }
            ?: run {
                val containingMod = findCommonAncestorStrictOfType<RsMod>(elements) ?: return
                val items = elements.filterIsInstance<RsItemElement>().toSet()
                items to containingMod
            }

        if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, itemsToMove, true)) return

        val relatedImplItems = collectRelatedImplItems(containingMod, itemsToMove)
        val itemsToMoveAll = itemsToMove + relatedImplItems
        val dialog = RsMoveTopLevelItemsDialog(project, itemsToMoveAll, containingMod)
        if (isUnitTestMode) {
            dialog.doAction()
        } else {
            dialog.show()
        }
    }

    private fun collectInitialItems(project: Project, editor: Editor): Pair<Set<RsItemElement>, RsMod>? {
        val file = editor.document.toPsiFile(project) ?: return null
        val selection = editor.selectionModel
        return if (selection.hasSelection()) {
            collectItemsInsideSelection(file, selection)
        } else {
            collectedItemsUnderCaret(file, editor.caretModel)
        }
    }

    private fun collectItemsInsideSelection(file: PsiFile, selection: SelectionModel): Pair<Set<RsItemElement>, RsMod>? {
        val (leafElement1, leafElement2) = file.getElementRange(selection.selectionStart, selection.selectionEnd)
            ?: return null
        val element1 = leafElement1.ancestorOrSelf<RsElement>() ?: return null
        val element2 = leafElement2.ancestorOrSelf<RsElement>() ?: return null
        val containingMod = findCommonAncestorStrictOfType<RsMod>(listOf(element1, element2)) ?: return null
        val item1 = element1.getTopmostParentInside(containingMod)
        val item2 = element2.getTopmostParentInside(containingMod)
        val items = collectElements(item1, item2.nextSibling) { it is RsItemElement }
            .mapTo(mutableSetOf()) { it as RsItemElement }
        return items to containingMod
    }

    private fun collectedItemsUnderCaret(file: PsiFile, caretModel: CaretModel): Pair<Set<RsItemElement>, RsMod>? {
        val elements = caretModel.allCarets.mapNotNull {
            val offset = it.offset
            val leafElement = file.findElementAt(offset)
            val element = if (offset > 0 && leafElement is PsiWhiteSpace) {
                // Workaround for situation when cursor is after item
                file.findElementAt(offset - 1)
            } else {
                leafElement
            }
            element?.ancestorOrSelf<RsItemElement>()
        }
        val containingMod = findCommonAncestorStrictOfType<RsMod>(elements) ?: return null
        val items = elements.mapNotNull { it.getTopmostParentInside(containingMod) as? RsItemElement }
        if (items.isEmpty()) return null
        return items.toSet() to containingMod
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

/** Mostly needed for case when [elements] contains only one [RsMod] */
private inline fun <reified T : RsElement> findCommonAncestorStrictOfType(elements: List<PsiElement>): T? {
    val parent = PsiTreeUtil.findCommonParent(elements) ?: return null
    return if (elements.contains(parent)) {
        parent.ancestorStrict()
    } else {
        parent.ancestorOrSelf()
    }
}

private fun collectRelatedImplItems(containingMod: RsMod, items: Set<RsItemElement>): List<RsImplItem> {
    if (isUnitTestMode) return emptyList()
    // For struct `Foo` we should collect:
    // * impl Foo { ... }
    // * impl ... for Foo { ... }
    // * Maybe also `impl From<Foo> for Bar { ... }`?
    //   if `Bar` belongs to same crate (but to different module from `Foo`)
    //
    // For trait `Foo` we should collect:
    // * impl Foo for ... { ... }
    return groupImplsByStructOrTrait(containingMod, items).values.flatten()
}

private fun groupImplsByStructOrTrait(containingMod: RsMod, items: Set<RsItemElement>): Map<RsItemElement, List<RsImplItem>> {
    return containingMod
        .childrenOfType<RsImplItem>()
        .mapNotNull { impl ->
            val struct: RsItemElement? = (impl.typeReference?.type as? TyAdt)?.item
            val trait = impl.traitRef?.path?.reference?.resolve() as? RsTraitItem
            val relatedStruct = struct?.takeIf { items.contains(it) }
            val relatedTrait = trait?.takeIf { items.contains(it) }
            val relatedItem = relatedStruct ?: relatedTrait ?: return@mapNotNull null
            relatedItem to impl
        }
        .groupBy({ it.first }, { it.second })
}
