/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.infer

import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.kind
import org.rust.lang.core.types.ty.*
import org.rust.lang.core.types.ty.Mutability.IMMUTABLE

fun inferOutOfFnExpressionType(expr: RsExpr) = when (expr) {
    is RsLitExpr -> inferLiteralExprType(expr)
    else -> TyUnknown
}

private fun inferLiteralExprType(expr: RsLitExpr): Ty {
    val kind = expr.kind
    return when (kind) {
        is RsLiteralKind.Boolean -> TyBool
        is RsLiteralKind.Integer -> TyInteger.fromLiteral(expr.integerLiteral!!)
        is RsLiteralKind.Float -> TyFloat.fromLiteral(expr.floatLiteral!!)
        is RsLiteralKind.Char -> if (kind.isByte) TyInteger(TyInteger.Kind.u8) else TyChar
        is RsLiteralKind.String -> {
            if (kind.isByte) {
                TyReference(TyArray(TyInteger(TyInteger.Kind.u8), kind.offsets.value?.length?.toLong() ?: 0), IMMUTABLE)
            } else {
                TyReference(TyStr, IMMUTABLE)
            }
        }
        null -> TyUnknown
    }
}
