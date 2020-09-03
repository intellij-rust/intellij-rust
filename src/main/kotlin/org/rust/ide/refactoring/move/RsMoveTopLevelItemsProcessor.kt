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
import com.intellij.util.containers.MultiMap
import org.rust.ide.refactoring.move.common.ElementToMove
import org.rust.ide.refactoring.move.common.RsMoveCommonProcessor
import org.rust.ide.refactoring.move.common.RsMoveUtil.addInner
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

    override fun findUsages(): Array<out UsageInfo> {
        if (!searchForReferences) return UsageInfo.EMPTY_ARRAY
        return commonProcessor.findUsages()
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val usages = refUsages.get()
        val conflicts = MultiMap<PsiElement, String>()
        return commonProcessor.preprocessUsages(usages, conflicts) && showConflicts(conflicts, usages)
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
        commonProcessor.updateMovedItemVisibility(item)

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
