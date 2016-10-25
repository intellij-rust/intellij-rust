package org.rust.lang.utils

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

fun RustBinaryExprElement.negateToString(): String {
    val lhs = left.text
    val rhs = right?.text ?: ""
    val op = when (operatorType) {
        RustTokenElementTypes.EQEQ      -> "!="
        RustTokenElementTypes.EXCLEQ    -> "=="
        RustTokenElementTypes.GT        -> "<="
        RustTokenElementTypes.LT        -> ">="
        RustTokenElementTypes.GTEQ      -> "<"
        RustTokenElementTypes.LTEQ      -> ">"
        else -> null
    }
    return if (op != null) "$lhs $op $rhs" else "!($text)"
}

fun PsiElement.isNegation(): Boolean =
    this is RustUnaryExprElement && excl != null

fun PsiElement.negate() : PsiElement {
    when {
        isNegation() -> {
            val e = (this as RustUnaryExprElement).expr
            return if (e is RustParenExprElement) return e.expr
            else checkNotNull(e)
        }

        this is RustBinaryExprElement ->
            return checkNotNull(RustElementFactory.createExpression(project, negateToString()))

        this is RustParenExprElement ||
        this is RustPathExprElement  ||
        this is RustCallExprElement      ->
            return checkNotNull(RustElementFactory.createExpression(project, "!$text"))

        else ->
            return checkNotNull(RustElementFactory.createExpression(project, "!($text)"))
    }
}
