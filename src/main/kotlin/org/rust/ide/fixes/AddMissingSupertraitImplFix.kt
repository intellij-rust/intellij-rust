/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.presentation.ImportingPsiRenderer
import org.rust.ide.presentation.PsiRenderingOptions
import org.rust.ide.presentation.renderTraitRef
import org.rust.ide.presentation.renderTypeReference
import org.rust.ide.refactoring.implementMembers.generateMissingTraitMembers
import org.rust.ide.utils.GenericConstraints
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.ide.utils.import.import
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.resolveToBoundTrait
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.types.*
import org.rust.lang.core.types.infer.substitute

/**
 * Implements missing supertrait(s) for a type in an impl item.
 *
 * ```
 * trait A {}
 * trait B: A {}
 *
 * struct S;
 *
 * impl B/*caret*/ for S {}
 *
 * // fix adds
 * impl A for S {}
 * ```
 */
class AddMissingSupertraitImplFix(implItem: RsImplItem) : RsQuickFixBase<RsImplItem>(implItem) {
    override fun getText(): String = RsBundle.message("intention.name.implement.missing.supertrait.s")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsImplItem) {
        val traitRef = element.traitRef ?: return
        val trait = element.implementedTrait ?: return

        val typeRef = element.typeReference ?: return
        val implLookup = element.implLookup
        val type = typeRef.normType(implLookup)

        val traits = mutableListOf<Pair<BoundElement<RsTraitItem>, RsTraitRef>>()
        val substitutions = mutableListOf(pathPsiSubst(traitRef.path, trait.element))
        collectSuperTraits(trait, traitRef, substitutions, traits, mutableSetOf())

        for ((superTrait, ref) in traits) {
            if (superTrait == trait) continue
            if (!implLookup.canSelect(TraitRef(type, superTrait))) {
                implementTrait(element, ref, typeRef, trait, substitutions, editor)
            }
        }
    }
}

private fun implementTrait(
    context: RsImplItem,
    superTraitRef: RsTraitRef,
    typeReference: RsTypeReference,
    trait: BoundElement<RsTraitItem>,
    substitutions: List<RsPsiSubstitution>,
    editor: Editor?,
) {
    val renderer = ImportingPsiRenderer(
        PsiRenderingOptions(),
        substitutions,
        context,
    )

    val factory = RsPsiFactory(context.project)

    val typeReferences = superTraitRef.descendantsOfType<RsTypeReference>() + typeReference.descendantsOfType()
    val types = typeReferences.map { it.rawType.substitute(trait.subst) }
    val constraints = GenericConstraints.create(context).filterByTypes(types)

    val typeParameters = constraints.buildTypeParameters()
    val traitText = renderer.renderTraitRef(superTraitRef)
    val typeText = renderer.renderTypeReference(typeReference)
    val whereClause = constraints.buildWhereClause()

    val text = "impl$typeParameters $traitText for $typeText $whereClause{}"
    val impl = factory.tryCreateImplItem(text) ?: return

    val placeForImpl = PsiInsertionPlace.forItemBefore(context) ?: return

    val inserted = placeForImpl.insert(impl)
    for (importCandidate in renderer.itemsToImport) {
        importCandidate.import(inserted)
    }
    generateMissingTraitMembers(inserted, superTraitRef, editor)
}

private fun collectSuperTraits(
    trait: BoundElement<RsTraitItem>,
    ref: RsTraitRef,
    substitutions: MutableList<RsPsiSubstitution>,
    ordered: MutableList<Pair<BoundElement<RsTraitItem>, RsTraitRef>>,
    visited: MutableSet<BoundElement<RsTraitItem>>
) {
    if (trait !in visited) {
        visited.add(trait)
        ordered.add(Pair(trait, ref))
    } else {
        return
    }

    trait.element.typeParamBounds?.polyboundList?.forEach {
        val traitRef = it.bound.traitRef ?: return@forEach
        val superTrait = traitRef.resolveToBoundTrait() ?: return@forEach
        substitutions.add(pathPsiSubst(traitRef.path, superTrait.element))
        collectSuperTraits(superTrait.substitute(trait.subst), traitRef, substitutions, ordered, visited)
    }
}
