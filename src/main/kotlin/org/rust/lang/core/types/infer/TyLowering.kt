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
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.Substitution
import org.rust.lang.core.types.consts.Const
import org.rust.lang.core.types.consts.CtConstParameter
import org.rust.lang.core.types.consts.CtUnknown
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.regions.ReEarlyBound
import org.rust.lang.core.types.regions.ReStatic
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*
import org.rust.lang.utils.evaluation.evaluate
import org.rust.lang.utils.evaluation.tryEvaluate


// Keep in sync with TyFingerprint-create
fun inferTypeReferenceType(type: RsTypeReference, defaultTraitObjectRegion: Region? = null): Ty {
    return when (type) {
        is RsParenType -> type.typeReference?.let { inferTypeReferenceType(it, defaultTraitObjectRegion) } ?: TyUnknown
        is RsTupleType -> TyTuple(type.typeReferenceList.map { inferTypeReferenceType(it) })

        is RsBaseType -> when (val kind = type.kind) {
            RsBaseTypeKind.Unit -> TyUnit.INSTANCE
            RsBaseTypeKind.Never -> TyNever
            RsBaseTypeKind.Underscore -> TyPlaceholder(type)
            is RsBaseTypeKind.Path -> {
                val path = kind.path
                val primitiveType = TyPrimitive.fromPath(path)
                if (primitiveType != null) return primitiveType
                val boundElement = path.reference?.advancedResolve() ?: return TyUnknown
                val (target, subst, _) = boundElement

                when {
                    target is RsTraitOrImpl && path.hasCself -> {
                        if (target is RsImplItem) {
                            val typeReference = target.typeReference
                            if (typeReference == null || typeReference.isAncestorOf(path)) {
                                // `impl {}` or `impl Self {}`
                                TyUnknown
                            } else {
                                inferTypeReferenceType(typeReference)
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
                            .substituteWithTraitObjectRegion(subst, defaultTraitObjectRegion ?: ReStatic)
                        // Ignore associated type aliases, as these are usually not very useful
                        if (target is RsTypeAlias && !target.owner.isImplOrTrait) {
                            ty.withAlias(boundElement.downcast()!!)
                        } else {
                            ty
                        }
                    }
                    else -> return TyUnknown
                }
            }
        }

        is RsRefLikeType -> {
            val base = type.typeReference
            when {
                type.isRef -> {
                    val refRegion = type.lifetime.resolve()
                    val baseTy = if (base != null) inferTypeReferenceType(base, refRegion) else TyUnknown
                    TyReference(baseTy, type.mutability, refRegion)
                }
                type.isPointer -> {
                    val baseTy = if (base != null) inferTypeReferenceType(base, ReStatic) else TyUnknown
                    TyPointer(baseTy, type.mutability)
                }
                else -> TyUnknown
            }
        }

        is RsArrayType -> {
            val componentType = type.typeReference?.rawType ?: TyUnknown
            if (type.isSlice) {
                TySlice(componentType)
            } else {
                val const = type.expr?.evaluate(TyInteger.USize.INSTANCE) ?: CtUnknown
                TyArray(componentType, const)
            }
        }

        is RsFnPointerType -> {
            val paramTypes = type.valueParameters.map { it.typeReference?.rawType ?: TyUnknown }
            TyFunction(paramTypes, type.retType?.let { it.typeReference?.rawType ?: TyUnknown } ?: TyUnit.INSTANCE)
        }

        is RsTraitType -> {
            var hasSizedUnbound = false
            val traitBounds = type.polyboundList.mapNotNull {
                if (it.hasQ) {
                    hasSizedUnbound = true
                    null
                } else {
                    it.bound.traitRef?.resolveToBoundTrait()
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
            inferTypeReferenceType(expansion.type)
        }

        else -> TyUnknown
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
