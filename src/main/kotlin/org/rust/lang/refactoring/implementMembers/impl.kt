/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.refactoring.implementMembers

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.leftSiblings
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.rightSiblings
import org.rust.lang.core.types.BoundElement
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkWriteAccessAllowed

fun generateTraitMembers(impl: RsImplItem, editor: Editor?) {
    check(!ApplicationManager.getApplication().isWriteAccessAllowed)
    val (implInfo, trait) = findMembersToImplement(impl) ?: run {
        if (editor != null) {
            HintManager.getInstance().showErrorHint(editor, "No members to implement have been found")
        }
        return
    }

    val chosen = showTraitMemberChooser(implInfo, impl.project)
    if (chosen.isEmpty()) return
    runWriteAction {
        // Non-null was checked by `findMembersToImplement`.
        insertNewTraitMembers(chosen, impl.members!!, trait)
    }
}

private fun findMembersToImplement(impl: RsImplItem): Pair<TraitImplementationInfo, BoundElement<RsTraitItem>>? {
    checkReadAccessAllowed()

    val trait = impl.traitRef?.resolveToBoundTrait ?: return null
    val implInfo = TraitImplementationInfo.create(trait.element, impl) ?: return null
    if (implInfo.declared.isEmpty()) return null
    return implInfo to trait
}

private fun insertNewTraitMembers(selected: Collection<RsAbstractable>, existingMembers: RsMembers, trait: BoundElement<RsTraitItem>) {
    checkWriteAccessAllowed()
    if (selected.isEmpty()) return

    val templateImpl = RsPsiFactory(existingMembers.project).createMembers(selected, trait.subst)

    val traitMembers = trait.element.members!!.childrenOfType<RsAbstractable>()
    val newMembers = templateImpl.childrenOfType<RsAbstractable>()

    // [1] First, check if the order of the existingMembers already implemented
    // matches the order of existingMembers in the trait declaration.
    val existingMembersOrder = existingMembers.childrenOfType<RsAbstractable>().map {
        existingMember -> traitMembers.indexOfFirst { it.name == existingMember.name }
    }
    val areExistingMembersInTheRightOrder = existingMembersOrder == existingMembersOrder.sorted()

    for ((index, newMember) in newMembers.withIndex()) {
        val posInTrait = traitMembers.indexOfFirst { it.name == newMember.name }

        // If [1] does not hold, the first new member we will append at the end of the implementation.
        // All the other ones will consequently be inserted at the right position in relation to that very first one.
        val anchor = if (!areExistingMembersInTheRightOrder && index == 0) {
            existingMembers.childrenOfType<RsAbstractable>().lastOrNull()
        } else {
            existingMembers.childrenOfType<RsAbstractable>().findLast {
                existingMember -> traitMembers.indexOfFirst { it.name == existingMember.name } < posInTrait
            }
        } ?: existingMembers.lbrace ?: return

        // If the newly added item is a function, we add an extra line between it and each of its siblings.
        val addedMember = existingMembers.addAfter(newMember, anchor)

        val previousAbstractable = addedMember.leftSiblings.find { it is RsAbstractable }
        if (previousAbstractable != null) {
            if (previousAbstractable is RsFunction || addedMember is RsFunction) {
                val whitespaces = createExtraWhitespacesAroundFunction(previousAbstractable, addedMember)
                existingMembers.addBefore(whitespaces, addedMember)
            }
        }

        val nextAbstractable = addedMember.rightSiblings.find { it is RsAbstractable }
        if (nextAbstractable != null) {
            if (nextAbstractable is RsFunction || addedMember is RsFunction) {
                val whitespaces = createExtraWhitespacesAroundFunction(addedMember, nextAbstractable)
                existingMembers.addAfter(whitespaces, addedMember)
            }
        }
    }
}

private fun createExtraWhitespacesAroundFunction(left: PsiElement, right: PsiElement): PsiElement {
    val lineCount = left
        .rightSiblings
        .takeWhile { it != right }
        .filterIsInstance<PsiWhiteSpace>()
        .map { it.text.count { c -> c == '\n' } }
        .sum()
    val extraLineCount = Math.max(0, 2 - lineCount)
    return RsPsiFactory(left.project).createWhitespace("\n".repeat(extraLineCount))
}
