/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder
import org.rust.stdext.mapToSet

class RsTraitImplementationInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): RsVisitor = object : RsWithMacrosInspectionVisitor() {
        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        override fun visitImplItem2(impl: RsImplItem) {
            val traitRef = impl.traitRef ?: return
            val trait = traitRef.resolveToTrait() ?: return
            val traitName = trait.name ?: return

            val implInfo = TraitImplementationInfo.create(trait, impl) ?: return

            if (!impl.isNegativeImpl && implInfo.missingImplementations.isNotEmpty()) {
                val missing = implInfo.missingImplementations
                    .mapNotNull { missing -> missing.name?.let { "`$it`" } }
                    .joinToString(", ")
                RsDiagnostic.TraitItemsMissingImplError(impl.impl, impl.typeReference ?: impl.impl, missing, impl)
                    .addToHolder(holder)
            }

            val traitMembersNames = implInfo.declared.mapToSet { it.name!! }
            for ((key, implMember) in implInfo.implementedByNameAndType) {
                // ignore members expanded from macros
                if (implMember.containingFile != impl.containingFile) continue

                val memberName = key.first
                if (memberName in traitMembersNames) {
                    val traitMember = implInfo.declaredByNameAndType[key]

                    if (implMember is RsFunction && traitMember is RsFunction) {
                        checkTraitFnImplParams(holder, implMember, traitMember, traitName)
                    }

                    if (traitMember == null) {
                        val nameIdentifier = implMember.nameIdentifier ?: continue
                        RsDiagnostic.MismatchMemberInTraitImplError(nameIdentifier, implMember, traitName)
                            .addToHolder(holder)
                    }
                } else {
                    RsDiagnostic.UnknownMemberInTraitError(implMember.nameIdentifier!!, implMember, traitName)
                        .addToHolder(holder)
                }
            }
        }
    }

    private fun checkTraitFnImplParams(
        holder: RsProblemsHolder,
        fn: RsFunction,
        superFn: RsFunction,
        traitName: String
    ) {
        val params = fn.valueParameterList ?: return
        val selfArg = fn.selfParameter
        val superSelfParameter = superFn.selfParameter

        if (selfArg != null && superFn.selfParameter == null) {
            RsDiagnostic.DeclMissingFromTraitError(selfArg, fn, superFn, selfArg).addToHolder(holder)
        } else if (selfArg == null && superSelfParameter != null) {
            RsDiagnostic.DeclMissingFromImplError(params, fn, superFn, superSelfParameter).addToHolder(holder)
        }

        val paramsCount = fn.valueParameters.size
        val superParamsCount = superFn.valueParameters.size
        if (paramsCount != superParamsCount) {
            RsDiagnostic.TraitParamCountMismatchError(params, fn, traitName, paramsCount, superParamsCount)
                .addToHolder(holder)
        }
    }
}
