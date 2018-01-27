/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.kind
import org.rust.lang.core.stubs.RsLitExprStub
import org.rust.lang.core.stubs.RsStubLiteralType
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger

val RsLitExpr.stubType: RsStubLiteralType? get() {
    val stub = (stub as? RsLitExprStub)
    if (stub != null) return stub.type
    val kind = kind
    return when (kind) {
        is RsLiteralKind.Boolean ->  RsStubLiteralType.Boolean
        is RsLiteralKind.Char -> RsStubLiteralType.Char(kind.isByte)
        is RsLiteralKind.String -> RsStubLiteralType.String(kind.offsets.value?.length?.toLong(), kind.isByte)
        is RsLiteralKind.Integer -> RsStubLiteralType.Integer(TyInteger.Kind.fromSuffixedLiteral(integerLiteral!!))
        is RsLiteralKind.Float -> RsStubLiteralType.Float(TyFloat.Kind.fromSuffixedLiteral(floatLiteral!!))
        else -> null
    }
}

val RsLitExpr.integerLiteralValue: String? get() =
    (stub as? RsLitExprStub)?.integerLiteralValue ?: integerLiteral?.text
