package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.intentions.RemoveParenthesesFromExprIntention
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.fields

class RustExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        element.accept(RedundantParenthesisVisitor(holder))
        if (element is RustStructExprElement) {
            val struct = element.path.reference.resolve() as? RustStructItemElement
            if (struct != null) {
                checkStructExpr(holder, struct, element.structExprBody)
            }
        }
    }

    private fun checkStructExpr(
        holder: AnnotationHolder,
        struct: RustStructItemElement,
        expr: RustStructExprBodyElement
    ) {
        for (field in expr.structExprFieldList) {
            if (field.reference.resolve() == null) {
                holder.createErrorAnnotation(field.identifier, "No such field")
            }
        }

        val declaredFields = expr.structExprFieldList.map { it.referenceName }.toSet()
        if (declaredFields.size < expr.structExprFieldList.size) {
            for (field in expr.structExprFieldList) {
                if (expr.structExprFieldList.filter { it.referenceName == field.referenceName }.size > 1) {
                    holder.createErrorAnnotation(field.identifier, "Duplicate field")
                }
            }
        }

        if (expr.dotdot != null) return  // functional update, no need to declare all the fields.

        val requiredFields = struct.fields.mapNotNull { it.name }.toSet()

        if (!declaredFields.containsAll(requiredFields)) {
            holder.createErrorAnnotation(expr.rbrace ?: expr, "Some fields are missing")
        }

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
