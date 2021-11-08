/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.implementMembers

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.ide.presentation.ImportingPsiRenderer
import org.rust.ide.presentation.PsiRenderingOptions
import org.rust.ide.presentation.renderFunctionSignature
import org.rust.ide.presentation.renderTypeReference
import org.rust.ide.settings.RsCodeInsightSettings
import org.rust.ide.utils.import.ImportCandidateBase
import org.rust.ide.utils.import.import
import org.rust.lang.core.macros.expandedFromRecursively
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.infer.substitute
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.openapiext.checkWriteAccessAllowed
import org.rust.openapiext.checkWriteAccessNotAllowed
import org.rust.openapiext.selectElement
import kotlin.math.max

fun generateTraitMembers(impl: RsImplItem, editor: Editor?) {
    checkWriteAccessNotAllowed()
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
        insertNewTraitMembers(chosen, impl, trait, editor)
    }
}

/**
 * Generates missing trait members in a non-interactive way.
 */
fun generateMissingTraitMembers(impl: RsImplItem) {
    val (implInfo, trait) = findMembersToImplement(impl) ?: return

    runWriteAction {
        insertNewTraitMembers(implInfo.missingImplementations, impl, trait, null)
    }
}

private fun findMembersToImplement(impl: RsImplItem): Pair<TraitImplementationInfo, BoundElement<RsTraitItem>>? {
    checkReadAccessAllowed()

    val trait = impl.traitRef?.resolveToBoundTrait() ?: return null
    val implInfo = TraitImplementationInfo.create(trait.element, impl) ?: return null
    if (implInfo.declared.isEmpty()) return null
    return implInfo to trait
}

private fun insertNewTraitMembers(
    selected: Collection<RsAbstractable>,
    impl: RsImplItem,
    trait: BoundElement<RsTraitItem>,
    editor: Editor?
) {
    checkWriteAccessAllowed()
    if (selected.isEmpty()) return

    val gen = MembersGenerator(RsPsiFactory(impl.project), impl, trait)
    val templateImpl = gen.createTraitMembers(selected)

    val traitMembers = trait.element.expandedMembers
    val newMembers = templateImpl.childrenOfType<RsAbstractable>()

    // [1] First, check if the order of the existingMembers already implemented
    // matches the order of existingMembers in the trait declaration.
    val existingMembers = impl.members!!
    val existingMembersWithPosInTrait = existingMembers.expandedMembers.map { existingMember ->
        Pair(existingMember, traitMembers.indexOfFirst {
            it.elementType == existingMember.elementType && it.name == existingMember.name
        })
    }.toMutableList()
    val existingMembersOrder = existingMembersWithPosInTrait.map { it.second }
    val areExistingMembersInTheRightOrder = existingMembersOrder == existingMembersOrder.sorted()
    var needToSelect: RsElement? = null
    val insertedMembers = mutableListOf<RsAbstractable>()

    for ((index, newMember) in newMembers.withIndex()) {
        val posInTrait = traitMembers.indexOfFirst {
            it.elementType == newMember.elementType && it.name == newMember.name
        }

        var indexedExistingMembers = existingMembersWithPosInTrait.withIndex()

        // If [1] does not hold, the first new member we will append at the end of the implementation.
        // All the other ones will consequently be inserted at the right position in relation to that very first one.
        if (areExistingMembersInTheRightOrder || index > 0) {
            indexedExistingMembers = indexedExistingMembers.filter { it.value.second < posInTrait }
        }

        val anchor = indexedExistingMembers
            .lastOrNull()
            ?.let {
                val member = it.value.first
                IndexedValue(it.index, member.expandedFromRecursively ?: member)
            }
            ?: IndexedValue(-1, existingMembers.lbrace)

        val addedMember = existingMembers.addAfter(newMember, anchor.value) as RsAbstractable
        existingMembersWithPosInTrait.add(anchor.index + 1, Pair(addedMember, posInTrait))

        // If the newly added item is a function, we add an extra line between it and each of its siblings.
        val prev = addedMember.leftSiblings.find { it is RsAbstractable || it is RsMacroCall }
        if (prev != null && (prev is RsFunction || addedMember is RsFunction)) {
            val whitespaces = createExtraWhitespacesAroundFunction(prev, addedMember)
            existingMembers.addBefore(whitespaces, addedMember)
        }

        val next = addedMember.rightSiblings.find { it is RsAbstractable || it is RsMacroCall }
        if (next != null && (next is RsFunction || addedMember is RsFunction)) {
            val whitespaces = createExtraWhitespacesAroundFunction(addedMember, next)
            existingMembers.addAfter(whitespaces, addedMember)
        }

        if (needToSelect == null) {
            needToSelect = when (addedMember) {
                is RsFunction -> addedMember.block?.expr
                is RsTypeAlias -> addedMember.typeReference
                is RsConstant -> addedMember.expr
                else -> error("unreachable")
            }
        }
        insertedMembers += addedMember
    }

    if (RsCodeInsightSettings.getInstance().importOutOfScopeItems) {
        for (importCandidate in gen.itemsToImport) {
            importCandidate.import(existingMembers)
        }
    }

    simplifyConstExprs(insertedMembers)

    if (needToSelect != null && editor != null) {
        selectElement(needToSelect, editor)
    }
}

/** Replaces `{ 1 }` to `1` */
private fun simplifyConstExprs(insertedMembers: List<RsAbstractable>) {
    for (member in insertedMembers) {
        val constExprs = member.descendantsOfType<RsTypeArgumentList>()
            .flatMap { it.exprList } +
            member.descendantsOfType<RsArrayType>()
                .mapNotNull { it.expr }
        for (expr in constExprs) {
            if (expr is RsBlockExpr) {
                val wrappingExpr = expr.block.expr
                if (wrappingExpr != null && (wrappingExpr !is RsPathExpr || expr.parent is RsArrayType)) {
                    expr.replace(wrappingExpr)
                }
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
    val extraLineCount = max(0, 2 - lineCount)
    return RsPsiFactory(left.project).createWhitespace("\n".repeat(extraLineCount))
}

class MembersGenerator(
    private val factory: RsPsiFactory,
    impl: RsImplItem,
    private val trait: BoundElement<RsTraitItem>,
) {
    private val renderer = ImportingPsiRenderer(
        PsiRenderingOptions(shortPaths = false),
        listOf(pathPsiSubst(impl.traitRef!!.path, trait.element)),
        impl.members!!
    )
    val itemsToImport: Set<ImportCandidateBase> get() = renderer.itemsToImport

    fun createTraitMembers(members: Collection<RsAbstractable>): RsMembers {
        val body = members.joinToString(separator = "\n", transform = {
            "    ${renderAbstractable(it)}"
        })

        return factory.createMembers(body)
    }

    fun renderAbstractable(element: RsAbstractable): String {
        val subst = trait.subst
        return when (element) {
            is RsConstant -> {
                val initialValue = RsDefaultValueBuilder(element.knownItems, element.containingMod, factory, true)
                    .buildFor(element.typeReference?.type?.substitute(subst) ?: TyUnknown, emptyMap())
                "const ${element.nameLikeElement.text}: ${element.typeReference?.renderTypeReference() ?: "_"} = ${initialValue.text};"
            }
            is RsTypeAlias ->
                "type ${element.escapedName} = ();"
            is RsFunction ->
                "${element.renderFunctionSignature()} {\n        todo!()\n    }"
            else ->
                error("Unknown trait member")
        }
    }

    private fun RsFunction.renderFunctionSignature(): String = renderer.renderFunctionSignature(this)
    private fun RsTypeReference.renderTypeReference(): String = renderer.renderTypeReference(this)
}
