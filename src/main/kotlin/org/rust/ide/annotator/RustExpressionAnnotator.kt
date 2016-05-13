package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.RemoveParenthesesFromExprIntention
import org.rust.lang.core.psi.RustExpr
import org.rust.lang.core.psi.RustIfExpr
import org.rust.lang.core.psi.RustParenExpr

class RustExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        when (element) {
            is RustIfExpr -> {
                checkIfParentheses(element, holder)
            }
            is RustParenExpr -> {
                checkImmediateChildIsParen(element, holder)
            }
        }
    }

    private fun checkImmediateChildIsParen(element: RustParenExpr, holder: AnnotationHolder) {
        val childExpr = element.expr
        if (childExpr is RustParenExpr) {
            holder.createWeakWarningAnnotation(element, "Redundant parentheses in expression")
                .registerFix(RemoveParenthesesFromExprIntention())
        }
    }

    private fun checkIfParentheses(element: RustIfExpr, holder: AnnotationHolder) {
        val expr = element.expr
        if (expr is RustParenExpr) {
            holder.createWeakWarningAnnotation(expr, "Predicate expression has unnecessary parentheses")
                .registerFix(RemoveParenthesesFromExprIntention())
        }
    }

    private val RustIfExpr.expr: RustExpr?
        get() = exprList.firstOrNull()
}
