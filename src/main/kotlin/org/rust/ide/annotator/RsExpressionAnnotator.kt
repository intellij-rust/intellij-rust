package org.rust.ide.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import org.rust.ide.annotator.fixes.AddStructFieldsFix
import org.rust.ide.intentions.RemoveParenthesesFromExprIntention
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.RsStructKind
import org.rust.lang.core.psi.impl.mixin.kind
import java.util.*

class RsExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        element.accept(RedundantParenthesisVisitor(holder))
        if (element is RsStructExpr) {
            val decl = element.path.reference.resolve() as? RsFieldsOwner
            if (decl != null) {
                checkStructExpr(holder, decl, element.structExprBody)
            }
        }
    }

    private fun checkStructExpr(
        holder: AnnotationHolder,
        decl: RsFieldsOwner,
        expr: RsStructExprBody
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
        val missingFields = decl.namedFields.filter { it.name !in declaredFields && !it.queryAttributes.hasCfgAttr() }

        if (decl is RsStructItem && decl.kind == RsStructKind.UNION) {
            if (expr.structExprFieldList.size > 1) {
                holder.createErrorAnnotation(expr, "Union expressions should have exactly one field")
            }
        } else {
            if (missingFields.isNotEmpty()) {
                holder.createErrorAnnotation(expr, "Some fields are missing")
                    .registerFix(AddStructFieldsFix(missingFields, expr), expr.textRange)
            }
        }

    }
}


private class RedundantParenthesisVisitor(private val holder: AnnotationHolder) : RsVisitor() {
    override fun visitCondition(o: RsCondition) =
        o.expr.warnIfParens("Predicate expression has unnecessary parentheses")

    override fun visitRetExpr(o: RsRetExpr) =
        o.expr.warnIfParens("Return expression has unnecessary parentheses")

    override fun visitMatchExpr(o: RsMatchExpr) =
        o.expr.warnIfParens("Match expression has unnecessary parentheses")

    override fun visitForExpr(o: RsForExpr) =
        o.expr.warnIfParens("For loop expression has unnecessary parentheses")

    override fun visitParenExpr(o: RsParenExpr) =
        o.expr.warnIfParens("Redundant parentheses in expression")

    private fun RsExpr?.warnIfParens(message: String) {
        if (this is RsParenExpr) {
            holder.createWeakWarningAnnotation(this, message)
                .registerFix(RemoveParenthesesFromExprIntention())
        }
    }
}

private fun <T : RsReferenceElement> Collection<T>.findDuplicateReferences(): Collection<T> {
    val names = HashSet<String>(size)
    val result = SmartList<T>()
    for (item in this) {
        val name = item.referenceName
        if (name in names) {
            result += item
        }
        names += name
    }
    return result
}
