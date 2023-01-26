/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import org.rust.lang.core.completion.isFnLikeTrait
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

/**
 * Inspection that detects the E0191 and E0220 errors.
 */
class RsWrongAssocTypeArgumentsInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsWithMacrosInspectionVisitor() {
            override fun visitTraitRef(trait: RsTraitRef) {
                checkAssocTypes(holder, trait, trait.path)
            }

            override fun visitPathType(type: RsPathType) {
                if (type.path.referenceName == "Self") return
                checkAssocTypes(holder, type, type.path)
            }
        }

    private fun checkAssocTypes(holder: RsProblemsHolder, element: RsElement, path: RsPath) {
        val trait = path.reference?.resolve() as? RsTraitItem ?: return
        val arguments = path.typeArgumentList
        val assocArguments = arguments?.assocTypeBindingList
        val assocArgumentMap = assocArguments
            ?.mapNotNull { binding ->
                val name = binding.path.takeIf { path -> !path.hasColonColon }?.referenceName ?: return@mapNotNull null
                name to binding
            }
            .orEmpty()
            .toMap()

        val assocTypes = trait.associatedTypesTransitively.associateBy { it.identifier.text }

        if (assocArguments != null) {
            checkUnknownAssocTypes(holder, assocArgumentMap, assocTypes, trait)
        }

        val parent = element.parent
        // Do not check missing associated types in:
        // super trait and generic bounds
        // impl Trait for ...
        // Fn traits
        // impl Trait
        // type qual
        if (element.ancestorStrict<RsTypeParamBounds>() != null
            || (parent is RsImplItem && parent.traitRef == element)
            || trait.isFnLikeTrait
            || element.isImplTrait
            || element.parent is RsTypeQual) {
            return
        }

        checkMissingAssocTypes(holder, element, assocArgumentMap, assocTypes)
    }

    private fun checkUnknownAssocTypes(
        holder: RsProblemsHolder,
        assocArguments: Map<String, RsAssocTypeBinding>,
        assocTypes: Map<String, RsTypeAlias>,
        trait: RsTraitItem
    ) {
        for ((name, argument) in assocArguments) {
            if (name !in assocTypes) {
                val traitName = trait.name ?: continue
                RsDiagnostic.UnknownAssocTypeBinding(argument, name, traitName).addToHolder(holder)
            }
        }
    }

    private fun checkMissingAssocTypes(
        holder: RsProblemsHolder,
        element: RsElement,
        assocArgumentMap: Map<String, RsAssocTypeBinding>,
        requiredAssocTypes: Map<String, RsTypeAlias>
    ) {
        val missingTypes = mutableListOf<MissingAssocTypeBinding>()
        for ((name, assocType) in requiredAssocTypes) {
            if (name !in assocArgumentMap) {
                val trait = (assocType.owner as? RsAbstractableOwner.Trait)?.trait ?: continue
                val traitName = trait.name ?: continue
                missingTypes.add(MissingAssocTypeBinding(name, traitName))
            }
        }
        if (missingTypes.isNotEmpty()) {
            missingTypes.sortBy { it.name }
            RsDiagnostic.MissingAssocTypeBindings(element, missingTypes).addToHolder(holder)
        }
    }

    data class MissingAssocTypeBinding(val name: String, val trait: String)
}

/**
 * Detects
 * fn foo(_: impl Trait)
 */
private val PsiElement.isImplTrait: Boolean
    get() {
        if (this !is RsTraitRef) return false
        val grandparent = this.parent.parent
        if (grandparent !is RsPolybound) return false

        val traitType = grandparent.parent as? RsTraitType ?: return false
        return traitType.impl != null
    }
