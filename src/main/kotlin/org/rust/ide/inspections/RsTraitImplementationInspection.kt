/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.ImplLookup
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.infer.*
import org.rust.lang.core.types.toTypeSubst
import org.rust.lang.core.types.ty.TyFunction
import org.rust.lang.core.types.ty.TyTypeParameter
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.lang.utils.RsDiagnostic
import org.rust.lang.utils.addToHolder

class RsTraitImplementationInspection : RsLocalInspectionTool() {

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : RsVisitor() {
        override fun visitImplItem(impl: RsImplItem) {
            val traitRef = impl.traitRef ?: return
            val trait = traitRef.resolveToTrait() ?: return
            val traitName = trait.name ?: return

            val implInfo = TraitImplementationInfo.create(trait, impl) ?: return

            if (implInfo.missingImplementations.isNotEmpty()) {
                val missing = implInfo.missingImplementations.mapNotNull { it.name?.let { "`$it`" } }.joinToString(", ")
                RsDiagnostic.TraitItemsMissingImplError(impl.impl, impl.typeReference ?: impl.impl, missing, impl)
                    .addToHolder(holder)
            }

            for (member in implInfo.nonExistentInTrait) {
                RsDiagnostic.UnknownMethodInTraitError(member.nameIdentifier!!, member, traitName)
                    .addToHolder(holder)
            }

            for ((imp, dec) in implInfo.implementationToDeclaration) {
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
            return
        } else if (selfArg == null && superFn.selfParameter != null) {
            RsDiagnostic.DeclMissingFromImplError(params, fn, superFn.selfParameter).addToHolder(holder)
            return
        }

        var canCheckTypes = true
        val paramsCount = fn.valueParameters.size
        val superParamsCount = superFn.valueParameters.size
        if (paramsCount != superParamsCount) {
            RsDiagnostic.TraitParamCountMismatchError(params, fn, traitName, paramsCount, superParamsCount)
                .addToHolder(holder)
            canCheckTypes = false
        }

        val typeParamsCount = fn.typeParameters.size
        val superTypeParamsCount = superFn.typeParameters.size
        if (typeParamsCount != superTypeParamsCount) {
            val errorElement = fn.typeParameterList ?: fn.identifier
            RsDiagnostic.TypeParamCountMismatchError(errorElement, fn, traitName, typeParamsCount, superTypeParamsCount)
                .addToHolder(holder)
            canCheckTypes = false
        }

        if (canCheckTypes) {
            checkFunctionType(holder, fn, superFn)
        }
    }

    private fun checkFunctionType(holder: RsProblemsHolder, fn: RsFunction, superFn: RsFunction) {
        val owner = fn.owner as? RsAbstractableOwner.Impl ?: return
        if (owner.isInherent) return

        val structTy = owner.impl.typeReference?.type ?: return

        val implLookup = ImplLookup.relativeTo(fn)

        val substitutionForTrait = owner.impl.implementedTrait?.subst ?: Substitution()
        val substituted = superFn.type
            .substitute(substitutionForTrait)
            .foldTyTypeParameterWith {
                if (it.parameter is TyTypeParameter.Self) {
                    structTy
                } else {
                    it
                }
            }

        val actualTypeParamsSubst = superFn.generics
            .zip(fn.generics)
            .toMap()
            .toTypeSubst()

        val expected = substituted
            .let { implLookup.ctx.normalizeAssociatedTypesIn(it).value }
            .substitute(actualTypeParamsSubst)
            as TyFunction
        val actual = fn.type
            .let { implLookup.ctx.normalizeAssociatedTypesIn(it).value }
            as TyFunction

        // compare types
        actual.let { it.paramTypes + it.retType }
            .zip(expected.paramTypes + expected.retType)
            .map { (actual, expected) ->
                implLookup.ctx
                    .combineTypes(expected, actual)
                    as? CoerceResult.Mismatch
            }.zip(fn.typeElements)
            .mapNotNull { (mismatch, element) ->
                mismatch?.let {
                    // to prevent errors when there are unresolved types, because
                    // resolve errors should be emitted in different place
                    if (it.ty1 != TyUnknown && it.ty2 != TyUnknown)
                        it to element
                    else
                        null
                }
            }.forEach { (mismatch, element) ->
                RsDiagnostic.IncompatibleImplType(element, mismatch.ty1, mismatch.ty2)
                    .addToHolder(holder)
            }
    }

    private val RsFunction.typeElements: List<PsiElement>
        get() {
            val list = mutableListOf<PsiElement>()
            selfParameter?.let { list.add(it) }
            list.addAll(valueParameters)
            list.add(retType?.typeReference ?: identifier)
            return list
        }

}
