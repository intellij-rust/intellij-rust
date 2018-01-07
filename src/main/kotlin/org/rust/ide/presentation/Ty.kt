/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.lang.core.types.ty.*


val Ty.shortPresentableText: String get() = render(this, level = 3)
val Ty.insertionSafeText: String
    get() = render(this, level = Int.MAX_VALUE, unknown = "_", anonymous = "_", integer = "_", float = "_")

fun tyToString(ty: Ty) = render(ty, Int.MAX_VALUE)
fun tyToStringWithoutTypeArgs(ty: Ty) = render(ty, Int.MAX_VALUE, includeTypeArguments = false)

private fun render(
    ty: Ty,
    level: Int,
    unknown: String = "<unknown>",
    anonymous: String = "<anonymous>",
    integer: String = "{integer}",
    float: String = "{float}",
    includeTypeArguments: Boolean = true
): String {
    check(level >= 0)
    if (ty is TyUnknown) return unknown
    if (ty is TyPrimitive) {
        return when (ty) {
            is TyBool -> "bool"
            is TyChar -> "char"
            is TyUnit -> "()"
            is TyNever -> "!"
            is TyStr -> "str"
            is TyInteger -> ty.kind.toString()
            is TyFloat -> ty.kind.toString()
            else -> error("unreachable")
        }
    }

    if (level == 0) return "_"

    val r = { subTy: Ty -> render(subTy, level - 1, unknown, anonymous, integer, float) }

    return when (ty) {
        is TyFunction -> {
            val params = ty.paramTypes.joinToString(", ", "fn(", ")", transform = r)
            return if (ty.retType is TyUnit) params else "$params -> ${ty.retType}"

        }
        is TySlice -> "[${r(ty.elementType)}]"

        is TyTuple -> ty.types.joinToString(", ", "(", ")", transform = r)
        is TyArray -> "[${r(ty.base)}; ${ty.size ?: unknown}]"
        is TyReference -> "${if (ty.mutability.isMut) "&mut " else "&"}${
        render(ty.referenced, level, unknown, anonymous, integer, float)
        }"
        is TyPointer -> "*${if (ty.mutability.isMut) "mut" else "const"} ${r(ty.referenced)}"
        is TyTypeParameter -> ty.name ?: anonymous
        is TyTraitObject -> (ty.trait.element.name ?: return anonymous) +
            if (includeTypeArguments) formatTypeArguments(ty.typeArguments, r) else ""
        is TyStructOrEnumBase -> (ty.item.name ?: return anonymous) +
            if (includeTypeArguments) formatTypeArguments(ty.typeArguments, r) else ""
        is TyInfer -> when (ty) {
            is TyInfer.TyVar -> "_"
            is TyInfer.IntVar -> integer
            is TyInfer.FloatVar -> float
        }
        is FreshTyInfer -> "<fresh>" // really should never be displayed; debug only
        is TyAnon -> ty.traits.joinToString("+", "impl ") { it.element.name ?: anonymous }
        else -> error("unreachable")
    }
}

private fun formatTypeArguments(typeArguments: List<Ty>, r: (Ty) -> String) =
    if (typeArguments.isEmpty()) "" else typeArguments.joinToString(", ", "<", ">", transform = r)
