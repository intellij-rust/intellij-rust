package org.rust.ide.annotator

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
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
        for (field in expr.structExprFieldList) {
            if (field.reference.resolve() == null) {
                holder.createErrorAnnotation(field.identifier, "No such field")
            }
        }

        for (field in expr.structExprFieldList.findDuplicateReferences()) {
            holder.createErrorAnnotation(field.identifier, "Duplicate field")
        }

        if (expr.dotdot != null) return  // functional update, no need to declare all the fields.

        val declaredFields = expr.structExprFieldList.map { it.referenceName }.toSet()
        val missingFields = decl.fields.filter { it.name !in declaredFields }

        if (missingFields.isNotEmpty()) {
            holder.createErrorAnnotation(expr.rbrace ?: expr, "Some fields are missing")
                .registerFix(AddStructFieldsQuickFix(missingFields, expr), expr.textRange)
        }
    }
}

private class AddStructFieldsQuickFix(
    val fieldsToAdd: List<RustFieldDeclElement>,
    expr: RustStructExprBodyElement
) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {
    override fun getText(): String = "Add missing fields"

    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val expr = startElement as RustStructExprBodyElement
        val newBody = RustElementFactory.createStructExprBody(project, fieldsToAdd.mapNotNull { it.name }) ?: return
        val firstNewField = newBody.lbrace.nextSibling ?: return
        val lastNewField = newBody.rbrace?.prevSibling ?: return
        expr.addRangeAfter(firstNewField, lastNewField, expr.lbrace)
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
