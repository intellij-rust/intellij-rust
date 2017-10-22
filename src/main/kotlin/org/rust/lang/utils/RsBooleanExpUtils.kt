/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.utils

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ComparisonOp.*
import org.rust.lang.core.psi.ext.EqualityOp.*
import org.rust.lang.core.psi.ext.operatorType

fun RsBinaryExpr.negateToString(): String {
    val lhs = left.text
    val rhs = right?.text ?: ""
    val op = when (operatorType) {
        EQ -> "!="
        EXCLEQ -> "=="
        GT -> "<="
        LT -> ">="
        GTEQ -> "<"
        LTEQ -> ">"
        else -> null
    }
    return if (op != null) "$lhs $op $rhs" else "!($text)"
}

fun PsiElement.isNegation(): Boolean =
    this is RsUnaryExpr && excl != null

fun PsiElement.negate(): PsiElement {
    val psiFactory = RsPsiFactory(project)
    return when {
        isNegation() -> {
            val inner = (this as RsUnaryExpr).expr!!
            (inner as? RsParenExpr)?.expr ?: inner
        }

        this is RsBinaryExpr ->
            psiFactory.createExpression(negateToString())

        this is RsParenExpr || this is RsPathExpr || this is RsCallExpr ->
            psiFactory.createExpression("!$text")

        else ->
            psiFactory.createExpression("!($text)")
    }
}
