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

            for (member in implInfo.nonExistentInTrait) {
                // ignore members expanded from macros
                if (member.containingFile != impl.containingFile) continue

                RsDiagnostic.UnknownMemberInTraitError(member.nameIdentifier!!, member, traitName)
                    .addToHolder(holder)
            }

            for ((imp, dec) in implInfo.implementationToDeclaration) {
                // ignore members expanded from macros
                if (imp.containingFile != impl.containingFile) continue

                if (imp is RsFunction && dec is RsFunction) {
                    checkTraitFnImplParams(holder, imp, dec, traitName)
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

        if (selfArg != null && superFn.selfParameter == null) {
            RsDiagnostic.DeclMissingFromTraitError(selfArg, fn, selfArg).addToHolder(holder)
        } else if (selfArg == null && superFn.selfParameter != null) {
            RsDiagnostic.DeclMissingFromImplError(params, fn, superFn.selfParameter).addToHolder(holder)
        }

        val paramsCount = fn.valueParameters.size
        val superParamsCount = superFn.valueParameters.size
        if (paramsCount != superParamsCount) {
            RsDiagnostic.TraitParamCountMismatchError(params, fn, traitName, paramsCount, superParamsCount)
                .addToHolder(holder)
        }
    }
}
