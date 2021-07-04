/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.rust.lang.core.completion.isFnLikeTrait
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

/**
 * Inspection that detects the E0191 and E0220 errors.
 */
class RsWrongAssocTypeArgumentsInspection : RsLocalInspectionTool() {
    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) =
        object : RsVisitor() {
            override fun visitTraitRef(trait: RsTraitRef) {
                checkAssocTypes(holder, trait, trait.path)
            }

            override fun visitBaseType(type: RsBaseType) {
                val path = type.path ?: return
                if (path.referenceName == "Self") return

                checkAssocTypes(holder, type, path)
            }
        }

    private fun checkAssocTypes(holder: RsProblemsHolder, element: RsElement, path: RsPath) {
        val trait = path.reference?.resolve() as? RsTraitItem ?: return
        val arguments = path.typeArgumentList
        val assocArguments = arguments?.assocTypeBindingList

        val assocTypes = trait.associatedTypesTransitively.associateBy { it.identifier.text }
        val requiredAssocTypes = assocTypes.filter { it.value.typeReference == null }

        if (assocArguments != null) {
            checkUnknownAssocTypes(holder, assocArguments, assocTypes, trait)
        }

        val parent = element.parent
        // Do not check missing associated types in:
        // super trait and generic bounds
        // impl Trait for ...
        // Fn traits
        // impl Trait
        // type qual
        if (element.parentOfType<RsTypeParamBounds>() != null ||
            (parent is RsImplItem && parent.traitRef == element) ||
            trait.isFnLikeTrait ||
            element.isImplTrait ||
            element.parent is RsTypeQual) {
            return
        }

        checkMissingAssocTypes(holder, element, assocArguments, requiredAssocTypes)
    }

    private fun checkUnknownAssocTypes(
        holder: RsProblemsHolder,
        assocArguments: List<RsAssocTypeBinding>,
        assocTypes: Map<String, RsTypeAlias>,
        trait: RsTraitItem
    ) {
        for (argument in assocArguments) {
            val name = argument.referenceName
            if (name !in assocTypes) {
                val traitName = trait.name ?: continue
                RsDiagnostic.UnknownAssocTypeBinding(argument, name, traitName).addToHolder(holder)
            }
        }
    }

    private fun checkMissingAssocTypes(
        holder: RsProblemsHolder,
        element: RsElement,
        assocArguments: List<RsAssocTypeBinding>?,
        requiredAssocTypes: Map<String, RsTypeAlias>
    ) {
        val assocArgumentMap = assocArguments?.associateBy { it.identifier.text }.orEmpty()
        val missingTypes = mutableListOf<MissingAssocTypeBinding>()
        for (type in requiredAssocTypes) {
            if (type.key !in assocArgumentMap) {
                val trait = (type.value.owner as? RsAbstractableOwner.Trait)?.trait ?: continue
                val traitName = trait.name ?: continue
                missingTypes.add(MissingAssocTypeBinding(type.key, traitName))
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
