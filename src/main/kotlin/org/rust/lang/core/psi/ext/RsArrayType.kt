/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsArrayType
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.types.ty.TyInteger

val RsArrayType.isSlice: Boolean get() = stub?.isSlice ?: (expr == null)

val RsArrayType.arraySize: Long? get() {
    val stub = stub
    if (stub != null) {
        return if (stub.arraySize == -1L) null else stub.arraySize
    }
    return calculateArraySize(expr)
}

// TODO: support constants and compile time expressions
fun calculateArraySize(expr: RsExpr?): Long? = (expr as? RsLitExpr)
    ?.integerLiteral
    ?.text
    ?.removeSuffix(TyInteger.Kind.usize.name)
    ?.toLongOrNull()
