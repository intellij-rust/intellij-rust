package org.rust.lang.utils

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

fun RustBinaryExprElement.negateToString(): String {
    val lhs = left.text
    val rhs = right?.text ?: ""
    val op = when (operatorType) {
        RustTokenElementTypes.EQEQ -> "!="
        RustTokenElementTypes.EXCLEQ -> "=="
        RustTokenElementTypes.GT -> "<="
        RustTokenElementTypes.LT -> ">="
        RustTokenElementTypes.GTEQ -> "<"
        RustTokenElementTypes.LTEQ -> ">"
        else -> null
    }
    return if (op != null) "$lhs $op $rhs" else "!($text)"
}

fun PsiElement.isNegation(): Boolean =
    this is RustUnaryExprElement && excl != null

fun PsiElement.negate(): PsiElement {
    val psiFactory = RustPsiFactory(project)
    return when {
        isNegation() -> {
            val inner = (this as RustUnaryExprElement).expr!!
            (inner as? RustParenExprElement)?.expr ?: inner
        }

        this is RustBinaryExprElement ->
            psiFactory.createExpression(negateToString())

        this is RustParenExprElement || this is RustPathExprElement || this is RustCallExprElement ->
            psiFactory.createExpression("!$text")

        else ->
            psiFactory.createExpression("!($text)")
    }
}
