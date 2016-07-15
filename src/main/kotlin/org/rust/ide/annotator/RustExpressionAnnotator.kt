package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.RemoveParenthesesFromExprIntention
import org.rust.lang.core.psi.*

class RustExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        element.accept(RedundantParenthesisVisitor(holder))
    }
}

private class RedundantParenthesisVisitor(private val holder: AnnotationHolder) : RustElementVisitor() {
    override fun visitIfExpr(o: RustIfExprElement) =
        o.expr.warnIfParens("Predicate expression has unnecessary parentheses")

    override fun visitWhileExpr(o: RustWhileExprElement) =
        o.expr.warnIfParens("Predicate expression has unnecessary parentheses")

    override fun visitRetExpr(o: RustRetExprElement) =
        o.expr.warnIfParens("Return expression has unnecessary parentheses")

    override fun visitMatchExpr(o: RustMatchExprElement) =
        o.expr.warnIfParens("Match expression has unnecessary parentheses")

    override fun visitForExpr(o: RustForExprElement) =
        o.scopedForDecl.expr.warnIfParens("For loop expression has unnecessary parentheses")

    override fun visitParenExpr(o: RustParenExprElement) =
        o.expr.warnIfParens("Redundant parentheses in expression")

    private val RustIfExprElement.expr: RustExprElement?
        get() = exprList.firstOrNull()

    private fun RustExprElement?.warnIfParens(message: String) {
        if (this is RustParenExprElement) {
            holder.createWeakWarningAnnotation(this, message)
                .registerFix(RemoveParenthesesFromExprIntention())
        }
    }
}
