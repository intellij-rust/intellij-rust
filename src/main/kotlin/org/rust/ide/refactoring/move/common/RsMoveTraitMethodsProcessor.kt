/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move.common

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.DialogMessage
import com.intellij.psi.PsiElement
import com.intellij.util.containers.MultiMap
import org.rust.ide.refactoring.move.common.RsMoveUtil.isInsideMovedElements
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

/**
 * To resolve some references it is needed to have trait in scope. E.g.:
 * - `expr.trait_method()`
 * - `Trait::trait_method(expr)`
 * - `Trait::AssocConst`
 * This class finds such references inside [sourceMod] and adds necessary trait imports.
 * There are two types of references:
 * - Outside references from moved item to method of trait which is in scope of [sourceMod]
 * - Inside references from item in [sourceMod] to method of moved trait
 */
class RsMoveTraitMethodsProcessor(
    private val psiFactory: RsPsiFactory,
    private val sourceMod: RsMod,
    private val targetMod: RsMod,
    private val pathHelper: RsMovePathHelper
) {

    fun preprocessOutsideReferences(
        @Suppress("UnstableApiUsage") conflicts: MultiMap<PsiElement, @DialogMessage String>,
        elementsToMove: List<ElementToMove>
    ) {
        val references = getReferencesToTraitAssocItems(elementsToMove)
        preprocessReferencesToTraitAssocItems(
            references,
            conflicts,
            { trait -> RsImportHelper.findPath(targetMod, trait) },
            { trait -> !trait.isInsideMovedElements(elementsToMove) }
        )
    }

    fun preprocessInsideReferences(
        @Suppress("UnstableApiUsage") conflicts: MultiMap<PsiElement, @DialogMessage String>,
        elementsToMove: List<ElementToMove>
    ) {
        val traitsToMove = elementsToMove
            .filterIsInstance<ItemToMove>()
            .map { it.item }
            .filterIsInstance<RsTraitItem>()
            .toSet()
        if (traitsToMove.isEmpty()) return
        val references = sourceMod.descendantsOfType<RsReferenceElement>()
            .filter { it.isMethodOrPath() && !it.isInsideMovedElements(elementsToMove) }
        preprocessReferencesToTraitAssocItems(
            references,
            conflicts,
            { trait -> pathHelper.findPathAfterMove(sourceMod, trait)?.text },
            { trait -> trait in traitsToMove }
        )
    }

    private fun preprocessReferencesToTraitAssocItems(
        references: List<RsReferenceElement>,
        @Suppress("UnstableApiUsage") conflicts: MultiMap<PsiElement, @DialogMessage String>,
        findTraitUsePath: (RsTraitItem) -> String?,
        shouldProcessTrait: (RsTraitItem) -> Boolean
    ) {
        for (reference in references) {
            val assocItem = reference.reference?.resolve() as? RsAbstractable ?: continue
            if (assocItem !is RsFunction && assocItem !is RsConstant) continue
            val trait = assocItem.getTrait() ?: continue
            if (!shouldProcessTrait(trait)) continue

            val traitUsePath = findTraitUsePath(trait)
            if (traitUsePath == null) {
                addVisibilityConflict(conflicts, reference, assocItem.superItem ?: trait)
            } else {
                reference.putCopyableUserData(RS_METHOD_CALL_TRAIT_USE_PATH, Pair(trait, traitUsePath))
            }
        }
    }

    fun addTraitImportsForOutsideReferences(elementsToMove: List<ElementToMove>) =
        addTraitImportsForReferences(getReferencesToTraitAssocItems(elementsToMove))

    fun addTraitImportsForInsideReferences() {
        val references = sourceMod.descendantsOfType<RsReferenceElement>().filter { it.isMethodOrPath() }
        addTraitImportsForReferences(references)
    }

    private fun addTraitImportsForReferences(references: Collection<RsReferenceElement>) {
        for (reference in references) {
            val (trait, traitUsePath) = reference.getCopyableUserData(RS_METHOD_CALL_TRAIT_USE_PATH) ?: continue
            // can't check `reference.reference.resolve() != null`, because it is always not null
            if (listOf(trait).filterInScope(reference).isNotEmpty()) continue
            addImport(psiFactory, reference, traitUsePath)
        }
    }

    companion object {
        private val RS_METHOD_CALL_TRAIT_USE_PATH: Key<Pair<RsTraitItem, String>> = Key.create("RS_METHOD_CALL_TRAIT_USE_PATH")
    }
}

private fun getReferencesToTraitAssocItems(elementsToMove: List<ElementToMove>): List<RsReferenceElement> =
    movedElementsShallowDescendantsOfType<RsReferenceElement>(elementsToMove, processInlineModules = false)
        .filter(RsReferenceElement::isMethodOrPath)

private fun RsReferenceElement.isMethodOrPath(): Boolean = this is RsMethodCall || this is RsPath

private fun RsAbstractable.getTrait(): RsTraitItem? {
    return when (val owner = owner) {
        is RsAbstractableOwner.Trait -> owner.trait
        is RsAbstractableOwner.Impl -> owner.impl.traitRef?.resolveToTrait()
        else -> null
    }
}
