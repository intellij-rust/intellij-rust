/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.IncorrectOperationException
import org.rust.ide.refactoring.move.common.ElementToMove
import org.rust.ide.refactoring.move.common.RsMoveCommonProcessor
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.startOffset

/** See overview of move refactoring in comment for [RsMoveCommonProcessor] */
class RsMoveTopLevelItemsProcessor(
    private val project: Project,
    private val itemsToMove: Set<RsItemElement>,
    private val targetMod: RsMod,
    private val searchForReferences: Boolean
) : BaseRefactoringProcessor(project) {

    private val commonProcessor: RsMoveCommonProcessor = run {
        val elementsToMove = itemsToMove.map { ElementToMove.fromItem(it) }
        RsMoveCommonProcessor(project, elementsToMove, targetMod)
    }

    init {
        if (itemsToMove.isEmpty()) throw IncorrectOperationException("No items to move")
    }

    override fun findUsages(): Array<out UsageInfo> {
        if (!searchForReferences) return UsageInfo.EMPTY_ARRAY
        return commonProcessor.findUsages()
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        return commonProcessor.preprocessUsages(refUsages.get()) && super.preprocessUsages(refUsages)
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        commonProcessor.performRefactoring(usages, this::moveItems)
    }

    private fun moveItems(): List<ElementToMove> {
        return itemsToMove
            .sortedBy { it.startOffset }
            .map { item -> moveItem(item) }
    }

    private fun moveItem(item: RsItemElement): ElementToMove {
        val space = item.nextSibling as? PsiWhiteSpace

        // have to call `copy` because of rare suspicious `PsiInvalidElementAccessException`
        val itemNew = targetMod.addInner(item.copy()) as RsItemElement
        if (space != null) targetMod.addInner(space.copy())

        space?.delete()
        item.delete()

        return ElementToMove.fromItem(itemNew)
    }

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        MoveMultipleElementsViewDescriptor(itemsToMove.toTypedArray(), targetMod.name ?: "")

    override fun getCommandName(): String = "Move items"
}

// like `PsiElement::add`, but works correctly for `RsModItem`
private fun RsMod.addInner(element: PsiElement): PsiElement =
    addBefore(element, if (this is RsModItem) rbrace else null)
