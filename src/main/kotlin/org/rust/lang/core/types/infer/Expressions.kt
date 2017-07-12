/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.*
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.type

fun inferOutOfFnExpressionType(expr: RsExpr) = when (expr) {
    is RsLitExpr -> inferLiteralExprType(expr)
    else -> TyUnknown
}

fun inferLiteralExprType(expr: RsLitExpr): Ty = when (expr.kind) {
    is RsLiteralKind.Boolean -> TyBool
    is RsLiteralKind.Integer -> TyInteger.fromLiteral(expr.integerLiteral!!)
    is RsLiteralKind.Float -> TyFloat.fromLiteral(expr.floatLiteral!!)
    is RsLiteralKind.String -> TyReference(TyStr)
    is RsLiteralKind.Char -> TyChar
    null -> TyUnknown
}

/**
 * Remap type parameters between type declaration and an impl block.
 *
 * Think about the following example:
 * ```
 * struct Flip<A, B> { ... }
 * impl<X, Y> Flip<Y, X> { ... }
 * ```
 */
fun RsImplItem.remapTypeParameters(subst: Substitution): Substitution {
    val positional = typeReference?.type?.typeParameterValues.orEmpty()
        .mapNotNull { (structParam, structType) ->
            if (structType is TyTypeParameter) {
                val implType = subst[structParam] ?: return@mapNotNull null
                structType to implType
            } else {
                null
            }
        }.toMap()

    val associated = (implementedTrait?.subst ?: emptyMap())
        .substituteInValues(positional)
    return positional + associated
}
