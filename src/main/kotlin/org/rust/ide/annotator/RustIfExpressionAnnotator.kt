package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.RemoveParenthesesFromExprIntention
import org.rust.lang.core.psi.RustExpr
import org.rust.lang.core.psi.RustIfExpr
import org.rust.lang.core.psi.RustParenExpr

class RustIfExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is RustIfExpr) {
            checkIfParentheses(element, holder)
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
