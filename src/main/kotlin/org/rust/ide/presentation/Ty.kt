/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.lang.core.psi.RsTraitItem
import org.rust.lang.core.psi.ext.lifetimeParameters
import org.rust.lang.core.psi.ext.typeParameters
import org.rust.lang.core.types.BoundElement
import org.rust.lang.core.types.regions.ReUnknown
import org.rust.lang.core.types.regions.Region
import org.rust.lang.core.types.ty.*

val Ty.shortPresentableText: String get() = render(this, level = 3)

val Ty.insertionSafeText: String
    get() = render(
        this,
        level = Int.MAX_VALUE,
        unknown = "_",
        anonymous = "_",
        unknownLifetime = "'_",
        integer = "_",
        float = "_"
    )

val Ty.insertionSafeTextWithLifetimes: String
    get() = render(
        this,
        level = Int.MAX_VALUE,
        unknown = "_",
        anonymous = "_",
        unknownLifetime = "'_",
        integer = "_",
        float = "_",
        includeLifetimeArguments = true
    )

fun tyToString(ty: Ty) = render(ty, Int.MAX_VALUE)
fun tyToStringWithoutTypeArgs(ty: Ty) = render(ty, Int.MAX_VALUE, includeTypeArguments = false)

private fun render(
    ty: Ty,
    level: Int,
    unknown: String = "<unknown>",
    anonymous: String = "<anonymous>",
    unknownLifetime: String = "'<unknown>",
    integer: String = "{integer}",
    float: String = "{float}",
    includeTypeArguments: Boolean = true,
    includeLifetimeArguments: Boolean = false
): String {
    check(level >= 0)
    if (ty == TyUnknown) return unknown
    if (ty is TyPrimitive) {
        return when (ty) {
            is TyBool -> "bool"
            is TyChar -> "char"
            is TyUnit -> "()"
            is TyNever -> "!"
            is TyStr -> "str"
            is TyInteger -> ty.name
            is TyFloat -> ty.name
            else -> error("unreachable")
        }
    }

    if (level == 0) return "_"

    val r = { subTy: Ty ->
        render(
            subTy,
            level - 1,
            unknown,
            anonymous,
            unknownLifetime,
            integer,
            float,
            includeTypeArguments,
            includeLifetimeArguments
        )
    }

    return when (ty) {
        is TyFunction -> {
            val params = ty.paramTypes.joinToString(", ", "fn(", ")", transform = r)
            return if (ty.retType == TyUnit) params else "$params -> ${ty.retType}"
        }
        is TySlice -> "[${r(ty.elementType)}]"

        is TyTuple -> ty.types.joinToString(", ", "(", ")", transform = r)
        is TyArray -> "[${r(ty.base)}; ${ty.size ?: unknown}]"
        is TyReference -> buildString {
            append('&')
            if (includeLifetimeArguments) append("${render(ty.region, unknownLifetime)} ")
            if (ty.mutability.isMut) append("mut ")
            append(
                render(
                    ty.referenced,
                    level,
                    unknown,
                    anonymous,
                    unknownLifetime,
                    integer,
                    float,
                    includeTypeArguments,
                    includeLifetimeArguments
                )
            )
        }
        is TyPointer -> "*${if (ty.mutability.isMut) "mut" else "const"} ${r(ty.referenced)}"
        is TyTypeParameter -> ty.name ?: anonymous
        is TyProjection -> "<${ty.type} as ${ty.trait.element.name ?: return anonymous}${
        if (includeTypeArguments) formatTraitGenerics(ty.trait, r, includeLifetimeArguments, false) else ""
        }>::${ty.target.name}"
        is TyTraitObject -> (ty.trait.element.name ?: return anonymous) +
            if (includeTypeArguments) formatTraitGenerics(ty.trait, r, includeLifetimeArguments) else ""
        is TyAdt -> (ty.item.name ?: return anonymous) +
            if (includeTypeArguments) formatGenerics(ty, r, includeLifetimeArguments) else ""
        is TyInfer -> when (ty) {
            is TyInfer.TyVar -> "_"
            is TyInfer.IntVar -> integer
            is TyInfer.FloatVar -> float
        }
        is FreshTyInfer -> "<fresh>" // really should never be displayed; debug only
        is TyAnon -> ty.traits.joinToString("+", "impl ") {
            (it.element.name ?: anonymous) +
                if (includeTypeArguments) formatTraitGenerics(it, r, includeLifetimeArguments) else ""
        }
        else -> error("unreachable")
    }
}

private fun render(region: Region, unknown: String = "'_"): String =
    if (region == ReUnknown) unknown else region.toString()

private fun formatGenerics(adt: TyAdt, r: (Ty) -> String, includeLifetimeArguments: Boolean): String {
    val typeArguments = adt.typeArguments.map(r)
    val lifetimeArguments = if (includeLifetimeArguments) {
        adt.lifetimeArguments.map { it.toString() }
    } else {
        emptyList()
    }
    val generics = lifetimeArguments + typeArguments
    return if (generics.isEmpty()) "" else generics.joinToString(", ", "<", ">")
}

private fun formatTraitGenerics(
    e: BoundElement<RsTraitItem>,
    r: (Ty) -> String,
    includeLifetimeArguments: Boolean,
    includeAssoc: Boolean = true
): String {
    val tySubst = e.element.typeParameters.map { r(e.subst[it] ?: TyUnknown) }
    val reSubst = if (includeLifetimeArguments) {
        e.element.lifetimeParameters.map { (e.subst[it] ?: ReUnknown).toString() }
    } else {
        emptyList()
    }
    val assoc = if (includeAssoc) {
        e.element.associatedTypesTransitively.mapNotNull {
            val name = it.name ?: return@mapNotNull null
            name + "=" + r(e.assoc[it] ?: TyUnknown)
        }
    } else {
        emptyList()
    }
    val visibleTypes = reSubst + tySubst + assoc
    return if (visibleTypes.isEmpty()) "" else visibleTypes.joinToString(", ", "<", ">")
}
