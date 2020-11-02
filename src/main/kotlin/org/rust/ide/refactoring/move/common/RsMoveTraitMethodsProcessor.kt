/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.rust.ide.refactoring.move.common.RsMoveUtil.isInsideMovedElements
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.*

class RsMoveTraitMethodsProcessor(
    private val psiFactory: RsPsiFactory,
    private val sourceMod: RsMod,
    private val targetMod: RsMod,
    private val pathHelper: RsMovePathHelper
) {

    fun preprocessOutsideReferencesToTraitMethods(
        conflicts: MultiMap<PsiElement, String>,
        elementsToMove: List<ElementToMove>
    ) {
        val methodCalls = movedElementsShallowDescendantsOfType<RsMethodCall>(elementsToMove)
            .filter { it.containingMod == sourceMod }
        preprocessReferencesToTraitMethods(
            methodCalls,
            conflicts,
            { trait -> RsImportHelper.findPath(targetMod, trait) },
            { trait -> !trait.isInsideMovedElements(elementsToMove) }
        )
    }

    fun preprocessInsideReferencesToTraitMethods(
        conflicts: MultiMap<PsiElement, String>,
        elementsToMove: List<ElementToMove>
    ) {
        val traitsToMove = elementsToMove
            .filterIsInstance<ItemToMove>()
            .map { it.item }
            .filterIsInstance<RsTraitItem>()
            .toSet()
        if (traitsToMove.isEmpty()) return
        val methodCalls = sourceMod.descendantsOfType<RsMethodCall>()
            .filter { !it.isInsideMovedElements(elementsToMove) }
        preprocessReferencesToTraitMethods(
            methodCalls,
            conflicts,
            { trait -> pathHelper.findPathAfterMove(sourceMod, trait)?.text },
            { trait -> trait in traitsToMove }
        )
    }

    private fun preprocessReferencesToTraitMethods(
        methodCalls: List<RsMethodCall>,
        conflicts: MultiMap<PsiElement, String>,
        findTraitUsePath: (RsTraitItem) -> String?,
        shouldProcessTrait: (RsTraitItem) -> Boolean
    ) {
        for (methodCall in methodCalls) {
            val method = methodCall.reference.resolve() as? RsAbstractable ?: continue
            val trait = method.getTrait() ?: continue
            if (!shouldProcessTrait(trait)) continue

            val traitUsePath = findTraitUsePath(trait)
            if (traitUsePath == null) {
                addVisibilityConflict(conflicts, methodCall, method.superItem ?: trait)
            } else {
                methodCall.putCopyableUserData(RS_METHOD_CALL_TRAIT_USE_PATH, Pair(trait, traitUsePath))
            }
        }
    }

    fun addTraitImportsForOutsideReferences(elementsToMove: List<ElementToMove>) =
        addTraitImportsForReferences(movedElementsShallowDescendantsOfType(elementsToMove))

    fun addTraitImportsForInsideReferences() =
        addTraitImportsForReferences(sourceMod.descendantsOfType())

    private fun addTraitImportsForReferences(methodCalls: Collection<RsMethodCall>) {
        for (methodCall in methodCalls) {
            val (trait, traitUsePath) = methodCall.getCopyableUserData(RS_METHOD_CALL_TRAIT_USE_PATH) ?: continue
            // can't check `methodCall.reference.resolve() != null`, because it is always not null
            if (listOf(trait).filterInScope(methodCall).isNotEmpty()) continue
            addImport(psiFactory, methodCall, traitUsePath)
        }
    }

    companion object {
        private val RS_METHOD_CALL_TRAIT_USE_PATH: Key<Pair<RsTraitItem, String>> = Key.create("RS_METHOD_CALL_TRAIT_USE_PATH")
    }
}

private fun RsAbstractable.getTrait(): RsTraitItem? {
    return when (val owner = owner) {
        is RsAbstractableOwner.Trait -> owner.trait
        is RsAbstractableOwner.Impl -> owner.impl.traitRef?.resolveToTrait()
        else -> null
    }
}
