package org.rust.ide.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.rust.ide.annotator.fixes.AddStructFieldsFix
import org.rust.ide.inspections.duplicates.findDuplicateReferences
import org.rust.ide.intentions.RemoveParenthesesFromExprIntention
import org.rust.lang.core.psi.*

class RustExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        element.accept(RedundantParenthesisVisitor(holder))
        if (element is RustStructExprElement) {
            val decl = element.path.reference.resolve() as? RustFieldsOwner
            if (decl != null) {
                checkStructExpr(holder, decl, element.structExprBody)
            }
        }
    }

    private fun checkStructExpr(
        holder: AnnotationHolder,
        decl: RustFieldsOwner,
        expr: RustStructExprBodyElement
    ) {
        expr.structExprFieldList
            .filter { it.reference.resolve() == null }
            .forEach {
                holder.createErrorAnnotation(it.identifier, "No such field")
                    .highlightType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
            }

        for (field in expr.structExprFieldList.findDuplicateReferences()) {
            holder.createErrorAnnotation(field.identifier, "Duplicate field")
        }

        if (expr.dotdot != null) return  // functional update, no need to declare all the fields.

        val declaredFields = expr.structExprFieldList.map { it.referenceName }.toSet()
        val missingFields = decl.namedFields.filter { it.name !in declaredFields }

        if (missingFields.isNotEmpty()) {
            holder.createErrorAnnotation(expr.rbrace ?: expr, "Some fields are missing")
                .registerFix(AddStructFieldsFix(missingFields, expr), expr.textRange)
        }
    }
}


private class RedundantParenthesisVisitor(private val holder: AnnotationHolder) : RustElementVisitor() {
    override fun visitCondition(o: RustConditionElement) =
        o.expr.warnIfParens("Predicate expression has unnecessary parentheses")

    override fun visitRetExpr(o: RustRetExprElement) =
        o.expr.warnIfParens("Return expression has unnecessary parentheses")

    override fun visitMatchExpr(o: RustMatchExprElement) =
        o.expr.warnIfParens("Match expression has unnecessary parentheses")

    override fun visitForExpr(o: RustForExprElement) =
        o.expr.warnIfParens("For loop expression has unnecessary parentheses")

    override fun visitParenExpr(o: RustParenExprElement) =
        o.expr.warnIfParens("Redundant parentheses in expression")

    private fun RustExprElement?.warnIfParens(message: String) {
        if (this is RustParenExprElement) {
            holder.createWeakWarningAnnotation(this, message)
                .registerFix(RemoveParenthesesFromExprIntention())
        }
    }
}
