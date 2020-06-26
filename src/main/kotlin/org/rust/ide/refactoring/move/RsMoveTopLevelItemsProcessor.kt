/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMultipleElementsViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.util.IncorrectOperationException
import org.rust.lang.core.psi.RsModItem
import org.rust.lang.core.psi.ext.RsItemElement
import org.rust.lang.core.psi.ext.RsMod

class RsMoveTopLevelItemsProcessor(
    private val project: Project,
    private val itemsToMove: Set<RsItemElement>,
    private val targetMod: RsMod,
    private val searchForReferences: Boolean
) : BaseRefactoringProcessor(project) {

    init {
        if (itemsToMove.isEmpty()) throw IncorrectOperationException("No items to move")
    }

    override fun findUsages(): Array<UsageInfo> {
        if (!searchForReferences) return UsageInfo.EMPTY_ARRAY
        return itemsToMove
            .flatMap { ReferencesSearch.search(it, GlobalSearchScope.projectScope(project)) }
            .filterNotNull()
            .map { UsageInfo(it) }
            .toTypedArray()
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        for (item in itemsToMove) {
            val space = item.nextSibling as? PsiWhiteSpace

            // have to call `copy` because of rare suspicious `PsiInvalidElementAccessException`
            targetMod.addInner(item.copy())
            if (space != null) targetMod.addInner(space.copy())

            space?.delete()
            item.delete()
        }
    }

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        MoveMultipleElementsViewDescriptor(itemsToMove.toTypedArray(), targetMod.name ?: "")

    override fun getCommandName(): String = "Move items"
}

// like `PsiElement::add`, but works correctly for `RsModItem`
private fun RsMod.addInner(element: PsiElement): PsiElement =
    addBefore(element, if (this is RsModItem) rbrace else null)
