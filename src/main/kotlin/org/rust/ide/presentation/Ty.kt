/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.lang.core.types.ty.*


val Ty.shortPresentableText: String get() = render(this, level = 3)

fun tyToString(ty: Ty) = render(ty, Int.MAX_VALUE)

private fun render(ty: Ty, level: Int): String {
    check(level >= 0)
    if (ty is TyUnknown) return "<unknown>"
    if (ty is TyPrimitive) {
        return when (ty) {
            is TyBool -> "bool"
            is TyChar -> "char"
            is TyUnit -> "()"
            is TyStr -> "str"
            is TyInteger -> ty.kind.toString()
            is TyFloat -> ty.kind.toString()
            else -> error("unreachable")
        }
    }

    if (level == 0) return "_"

    val r = { subTy: Ty -> render(subTy, level - 1) }
    val anonymous = "<anonymous>"

    return when (ty) {
        is TyFunction -> {
            val params = ty.paramTypes.map(r).joinToString(", ", "fn(", ")")
            return if (ty.retType is TyUnit) params else "$params -> ${ty.retType}"

        }
        is TySlice -> "[${r(ty.elementType)}]"

        is TyTuple -> ty.types.map(r).joinToString(", ", "(", ")")
        is TyArray -> "[${r(ty.base)}; ${ty.size}]"
        is TyReference -> "${if (ty.mutable) "&mut " else "&"}${render(ty.referenced, level)}"
        is TyPointer -> "*${if (ty.mutable) "mut" else "const"} ${r(ty.referenced)}"
        is TyTraitObject -> ty.trait.name ?: anonymous
        is TyTypeParameter -> ty.name ?: anonymous
        is TyStructOrEnumBase -> {
            val name = ty.item.name ?: return anonymous
            name + if (ty.typeArguments.isEmpty()) "" else ty.typeArguments.map(r).joinToString(", ", "<", ">")
        }
        else -> error("unreachable")
    }
}
