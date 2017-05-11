package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsArrayType
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.types.types.TyInteger

val RsArrayType.arraySize: Int? get() {
    val stub = stub
    if (stub != null) {
        return if (stub.arraySize == -1) null else stub.arraySize
    }
    return calculateArraySize(expr)
}

// TODO: support constants and compile time expressions
fun calculateArraySize(expr: RsExpr?): Int? {
    val sizeLiteral = (expr as? RsLitExpr)
        ?.integerLiteral
        ?.text
        ?.removeSuffix(TyInteger.Kind.usize.name)
    // BACKCOMPAT: Kotlin API 1.0
    return try {
        sizeLiteral?.toInt()
    } catch (e: NumberFormatException) {
        null
    }
}
