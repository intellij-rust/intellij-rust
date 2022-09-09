/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.macros.MacroExpansion
import org.rust.lang.core.macros.calculateMacroExpansionDepth
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.DEFAULT_RECURSION_LIMIT
import org.rust.lang.core.resolve.RsPathResolveResult
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.resolve.ref.RsPathReferenceImpl
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.RsPsiSubstitution
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.toSubst
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.evaluation.PathExprResolver
import org.rust.lang.utils.evaluation.evaluate
import org.rust.lang.utils.evaluation.tryEvaluate

/**
 * Lowers [RsTypeReference] to [Ty].
 * Prefer using [org.rust.lang.core.types.rawType]/[org.rust.lang.core.types.normType]
 */
class TyLowering private constructor(
    private val resolvedNestedPaths: Map<RsPath, List<RsPathResolveResult<RsElement>>>
) {
    // Keep in sync with TyFingerprint-create
    private fun lowerTy(type: RsTypeReference, defaultTraitObjectRegion: Region? = null): Ty {
        return when (type) {
            is RsParenType -> type.typeReference?.let { lowerTy(it, defaultTraitObjectRegion) }
                ?: TyUnknown

            is RsTupleType -> TyTuple(type.typeReferenceList.map { lowerTy(it) })

            is RsBaseType -> when (val kind = type.kind) {
                RsBaseTypeKind.Unit -> TyUnit.INSTANCE
                RsBaseTypeKind.Never -> TyNever
                RsBaseTypeKind.Underscore -> TyPlaceholder(type)
                is RsBaseTypeKind.Path -> {
                    val path = kind.path
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
                                if (typeReference == null || typeReference.isAncestorOf(path)) {
                                    // `impl {}` or `impl Self {}`
                                    TyUnknown
                                } else {
                                    lowerTy(typeReference)
                                }
                            } else {
                                TyTypeParameter.self(target)
                            }
                        }

                        target is RsTraitItem -> {
                            TyTraitObject(listOfNotNull(boundElement.downcast()), defaultTraitObjectRegion ?: ReUnknown)
                        }

                        target is RsTypeDeclarationElement -> {
                            val ty = target.declaredType
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
                val componentType = type.typeReference?.let { lowerTy(it) } ?: TyUnknown
                if (type.isSlice) {
                    TySlice(componentType)
                } else {
                    val const = type.expr?.evaluate(TyInteger.USize.INSTANCE) ?: CtUnknown
                    TyArray(componentType, const)
                }
            }

            is RsFnPointerType -> {
                val paramTypes = type.valueParameters.map { p -> p.typeReference?.let { lowerTy(it) } ?: TyUnknown }
                TyFunction(paramTypes, type.retType?.let { it -> it.typeReference?.let { lowerTy(it) } ?: TyUnknown } ?: TyUnit.INSTANCE)
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
                lowerTy(expansion.type)
            }

            else -> TyUnknown
        }
    }

    private fun rawMultiResolvePath(path: RsPath): List<RsPathResolveResult<RsElement>> {
        return resolvedNestedPaths[path]
            ?: path.reference?.rawMultiResolve()
            ?: emptyList()
    }

    private fun <T : RsElement> instantiatePathGenerics(
        path: RsPath,
        element: T,
        subst: Substitution,
        resolver: PathExprResolver,
        withAssoc: Boolean
    ): BoundElement<T> {
        if (element !is RsGenericDeclaration) return BoundElement(element, subst)

        val psiSubst = pathPsiSubst(path, element)
        val newSubst = psiSubst.toSubst(resolver, this)
        val assoc = if (withAssoc) {
            psiSubst.assoc.mapValues {
                when (val value = it.value) {
                    is RsPsiSubstitution.AssocValue.Present -> lowerTy(value.value)
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
            val resolvedNestedPaths = if (type is RsBaseType) {
                val kind = type.kind
                if (kind is RsBaseTypeKind.Path) {
                    RsPathReferenceImpl.resolveAllNestedPaths(kind.path)
                } else {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            return TyLowering(resolvedNestedPaths).lowerTy(type).foldTyPlaceholderWithTyInfer()
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
