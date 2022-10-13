/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import com.intellij.openapi.util.RecursionGuard
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.macros.calculateMacroExpansionDepth
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.RsPathResolveResult
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.ref.RsPathReferenceImpl
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.types.*
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.evaluation.PathExprResolver
import org.rust.lang.utils.evaluation.evaluate
import org.rust.lang.utils.evaluation.toConst
import org.rust.lang.utils.evaluation.tryEvaluate

/**
 * Lowers [RsTypeReference] to [Ty].
 * Prefer using [org.rust.lang.core.types.rawType]/[org.rust.lang.core.types.normType]
 */
class TyLowering private constructor(
    givenResolvedNestedPaths: Map<RsPath, List<RsPathResolveResult<RsElement>>>
) {
    private val resolvedNestedPaths: MutableMap<RsPath, List<RsPathResolveResult<RsElement>>> =
        HashMap(givenResolvedNestedPaths)
    private val declaredTypeCache: MutableMap<RsTypeDeclarationElement, Ty> = hashMapOf()
    private val genericParametersCache: MutableMap<RsGenericDeclaration, List<RsGenericParameter>> = hashMapOf()

    // Keep in sync with TyFingerprint-create
    private fun lowerTy(type: RsTypeReference, defaultTraitObjectRegion: Region?): Ty {
        return when (type) {
            is RsParenType -> type.typeReference?.let { lowerTy(it, defaultTraitObjectRegion) }
                ?: TyUnknown

            is RsTupleType -> TyTuple(type.typeReferenceList.map { lowerTy(it, null) })

            is RsUnitType -> TyUnit.INSTANCE
            is RsNeverType -> TyNever
            is RsInferType -> TyPlaceholder(type)

            is RsPathType -> {
                val path = type.path
                val rawResolveResult = rawMultiResolvePath(path)

                val primitiveType = TyPrimitive.fromPath(path, givenResolveResult = rawResolveResult)
                if (primitiveType != null) return primitiveType

                val singleResolveResult = rawResolveResult.singleOrNull() ?: return TyUnknown
                val target = singleResolveResult.element
                val boundElement = instantiatePathGenerics(
                    path,
                    singleResolveResult.element,
                    singleResolveResult.resolvedSubst,
                    PathExprResolver.default,
                    withAssoc = target is RsTraitItem
                )

                when {
                    target is RsTraitOrImpl && path.hasCself -> {
                        if (target is RsImplItem) {
                            val typeReference = target.typeReference
                            if (typeReference == null || path.contexts.contains(typeReference)) {
                                // `impl {}` or `impl Self {}`
                                TyUnknown
                            } else {
                                declaredTypeCache.getOrPut(target) { lowerTy(typeReference, null) }
                            }
                        } else {
                            declaredTypeCache.getOrPut(target) { TyTypeParameter.self(target) }
                        }
                    }

                    target is RsTraitItem -> {
                        TyTraitObject(listOfNotNull(boundElement.downcast()), defaultTraitObjectRegion ?: ReUnknown)
                    }

                    target is RsTypeDeclarationElement -> {
                        val ty = declaredTypeCache.getOrPut(target) { target.declaredType }
                            .substituteWithTraitObjectRegion(boundElement.subst, defaultTraitObjectRegion ?: ReStatic)
                        // Ignore associated type aliases, as these are usually not very useful
                        if (target is RsTypeAlias && !target.owner.isImplOrTrait) {
                            ty.withAlias(boundElement.downcast()!!)
                        } else {
                            ty
                        }
                    }

                    else -> TyUnknown
                }
            }

            is RsRefLikeType -> {
                val base = type.typeReference
                when {
                    type.isRef -> {
                        val refRegion = type.lifetime.resolve()
                        val baseTy = if (base != null) lowerTy(base, refRegion) else TyUnknown
                        TyReference(baseTy, type.mutability, refRegion)
                    }

                    type.isPointer -> {
                        val baseTy = if (base != null) lowerTy(base, ReStatic) else TyUnknown
                        TyPointer(baseTy, type.mutability)
                    }

                    else -> TyUnknown
                }
            }

            is RsArrayType -> {
                val componentType = type.typeReference?.let { lowerTy(it, null) } ?: TyUnknown
                if (type.isSlice) {
                    TySlice(componentType)
                } else {
                    val const = type.expr?.evaluate(TyInteger.USize.INSTANCE) ?: CtUnknown
                    TyArray(componentType, const)
                }
            }

            is RsFnPointerType -> {
                val paramTypes = type.valueParameters.map { p -> p.typeReference?.let { lowerTy(it, null) } ?: TyUnknown }
                TyFunction(paramTypes, type.retType?.let { it -> it.typeReference?.let { lowerTy(it, null) } ?: TyUnknown } ?: TyUnit.INSTANCE)
            }

            is RsTraitType -> {
                var hasSizedUnbound = false
                val traitBounds = type.polyboundList.mapNotNull {
                    if (it.hasQ) {
                        hasSizedUnbound = true
                        null
                    } else {
                        val path = it.bound.traitRef?.path ?: return@mapNotNull null
                        val res = rawMultiResolvePath(path).singleOrNull() ?: return@mapNotNull null
                        instantiatePathGenerics(path, res.element, res.resolvedSubst, PathExprResolver.default, withAssoc = true)
                            .downcast<RsTraitItem>()
                    }
                }
                if (type.isImpl) {
                    val sized = type.knownItems.Sized
                    val traitBoundsWithImplicitSized = if (!hasSizedUnbound && sized != null) {
                        traitBounds + BoundElement(sized)
                    } else {
                        traitBounds
                    }
                    return TyAnon(type, traitBoundsWithImplicitSized)
                }
                if (traitBounds.isEmpty()) return TyUnknown
                val lifetimeBounds = type.polyboundList.mapNotNull { it.bound.lifetime }
                val regionBound = lifetimeBounds.firstOrNull()?.resolve() ?: defaultTraitObjectRegion ?: ReStatic
                TyTraitObject(traitBounds, regionBound)
            }

            is RsMacroType -> {
                if (type.calculateMacroExpansionDepth() >= DEFAULT_RECURSION_LIMIT) return TyUnknown
                val expansion = type.macroCall.expansion as? MacroExpansion.Type ?: return TyUnknown
                lowerTy(expansion.type, null)
            }

            else -> TyUnknown
        }
    }

    private fun rawMultiResolvePath(path: RsPath): List<RsPathResolveResult<RsElement>> {
        val resolvedNestedPaths = resolvedNestedPaths
        val alreadyResolved = resolvedNestedPaths[path]
        if (alreadyResolved == null) {
            resolvedNestedPaths += RsPathReferenceImpl.resolveNeighborPaths(path)
        }
        return resolvedNestedPaths[path] ?: emptyList()
    }

    private fun <T : RsElement> instantiatePathGenerics(
        path: RsPath,
        element: T,
        subst: Substitution,
        resolver: PathExprResolver,
        withAssoc: Boolean
    ): BoundElement<T> {
        if (element !is RsGenericDeclaration) return BoundElement(element, subst)

        val genericParameters = genericParametersCache.getOrPut(element) { element.getGenericParameters() }
        val psiSubstitution = pathPsiSubst(path, element, givenGenericParameters = genericParameters)

        val typeSubst = psiSubstitution.typeSubst.entries.associate { (param, value) ->
            val paramTy = TyTypeParameter.named(param)
            val valueTy = when (value) {
                is RsPsiSubstitution.Value.DefaultValue -> {
                    val defaultValue = value.value.value
                    val defaultValueTy = guard.doPreventingRecursion(defaultValue, /* memoize = */true) {
                        defaultValue.rawType
                    } ?: TyUnknown
                    if (value.value.selfTy != null) {
                        defaultValueTy.substitute(mapOf(TyTypeParameter.self() to value.value.selfTy).toTypeSubst())
                    } else {
                        defaultValueTy
                    }
                }
                is RsPsiSubstitution.Value.OptionalAbsent -> paramTy
                is RsPsiSubstitution.Value.Present -> when (value.value) {
                    is RsPsiSubstitution.TypeValue.InAngles -> lowerTy(value.value.value, null)
                    is RsPsiSubstitution.TypeValue.FnSugar -> if (value.value.inputArgs.isNotEmpty()) {
                        TyTuple(value.value.inputArgs.map { if (it != null) lowerTy(it, null) else TyUnknown })
                    } else {
                        TyUnit.INSTANCE
                    }
                }
                RsPsiSubstitution.Value.RequiredAbsent -> TyUnknown
            }
            paramTy to valueTy
        }

        val regionSubst = psiSubstitution.regionSubst.entries.mapNotNull { (psiParam, psiValue) ->
            val param = ReEarlyBound(psiParam)
            val value = if (psiValue is RsPsiSubstitution.Value.Present) {
                psiValue.value.resolve()
            } else {
                return@mapNotNull null
            }

            param to value
        }.toMap()

        val constSubst = psiSubstitution.constSubst.entries.associate { (psiParam, psiValue) ->
            val param = CtConstParameter(psiParam)
            val value = when (psiValue) {
                RsPsiSubstitution.Value.OptionalAbsent -> param
                RsPsiSubstitution.Value.RequiredAbsent -> CtUnknown
                is RsPsiSubstitution.Value.Present -> {
                    val expectedTy = psiParam.typeReference?.normType ?: TyUnknown
                    psiValue.value.toConst(expectedTy, resolver)
                }
                is RsPsiSubstitution.Value.DefaultValue -> {
                    val expectedTy = psiParam.typeReference?.normType ?: TyUnknown
                    psiValue.value.toConst(expectedTy, resolver)
                }
            }

            param to value
        }

        val newSubst = Substitution(typeSubst, regionSubst, constSubst)

        val assoc = if (withAssoc) {
            psiSubstitution.assoc.mapValues {
                when (val value = it.value) {
                    is RsPsiSubstitution.AssocValue.Present -> lowerTy(value.value, null)
                    RsPsiSubstitution.AssocValue.FnSugarImplicitRet -> TyUnit.INSTANCE
                }
            }
        } else {
            emptyMap()
        }

        return BoundElement(element, subst + newSubst, assoc)
    }

    companion object {
        fun lowerTypeReference(type: RsTypeReference): Ty {
            return TyLowering(emptyMap()).lowerTy(type, null).foldTyPlaceholderWithTyInfer()
        }

        fun <T : RsElement> lowerPathGenerics(
            path: RsPath,
            element: T,
            subst: Substitution,
            resolver: PathExprResolver,
            resolvedNestedPaths: Map<RsPath, List<RsPathResolveResult<RsElement>>>,
        ): BoundElement<T> {
            return TyLowering(resolvedNestedPaths)
                .instantiatePathGenerics(path, element, subst, resolver, withAssoc = true)
                .foldTyPlaceholderWithTyInfer()
        }
    }
}

private val guard: RecursionGuard<PsiElement> =
    RecursionManager.createGuard("org.rust.lang.core.types.infer.TyLowering")

fun RsLifetime?.resolve(): Region {
    this ?: return ReUnknown
    if (referenceName == "'static") return ReStatic
    val resolved = reference.resolve()
    return if (resolved is RsLifetimeParameter) ReEarlyBound(resolved) else ReUnknown
}

private fun <T : TypeFoldable<T>> TypeFoldable<T>.substituteWithTraitObjectRegion(
    subst: Substitution,
    defaultTraitObjectRegion: Region
): T = foldWith(object : TypeFolder {
    override fun foldTy(ty: Ty): Ty = when {
        ty is TyTypeParameter -> handleTraitObject(ty) ?: ty
        ty.needsSubst -> ty.superFoldWith(this)
        else -> ty
    }

    override fun foldRegion(region: Region): Region =
        (region as? ReEarlyBound)?.let { subst[it] } ?: region

    override fun foldConst(const: Const): Const = when {
        const is CtConstParameter -> subst[const] ?: const
        const.hasCtConstParameters -> const.superFoldWith(this)
        else -> const
    }

    fun handleTraitObject(paramTy: TyTypeParameter): Ty? {
        val ty = subst[paramTy]
        if (ty !is TyTraitObject || ty.region !is ReUnknown) return ty
        val bounds = paramTy.regionBounds
        val region = when (bounds.size) {
            0 -> defaultTraitObjectRegion
            1 -> bounds.single().substitute(subst)
            else -> ReUnknown
        }
        return TyTraitObject(ty.traits, region)
    }
}).tryEvaluate()
