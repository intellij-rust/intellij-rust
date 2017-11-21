/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.util.SmartList
import org.rust.ide.annotator.fixes.AddStructFieldsFix
import org.rust.ide.intentions.RemoveParenthesesFromExprIntention
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import java.util.*


class RsExpressionAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        element.accept(RedundantParenthesisVisitor(holder))
        if (element is RsStructLiteral) {
            val decl = element.path.reference.resolve() as? RsFieldsOwner
            if (decl != null) {
                checkStructLiteral(holder, decl, element)
            }
        }
    }

    private fun checkStructLiteral(
        holder: AnnotationHolder,
        decl: RsFieldsOwner,
        literal: RsStructLiteral
    ) {
        val body = literal.structLiteralBody
        body.structLiteralFieldList
            .filter { it.reference.resolve() == null }
            .forEach {
                holder.createErrorAnnotation(it.identifier, "No such field")
                    .highlightType = ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
            }

        for (field in body.structLiteralFieldList.findDuplicateReferences()) {
            holder.createErrorAnnotation(field.identifier, "Duplicate field")
        }

        if (body.dotdot != null) return  // functional update, no need to declare all the fields.

        if (decl is RsStructItem && decl.kind == RsStructKind.UNION) {
            if (body.structLiteralFieldList.size > 1) {
                holder.createErrorAnnotation(body, "Union expressions should have exactly one field")
            }
        } else {
            if (calculateMissingFields(body, decl).isNotEmpty()) {
                val structNameRange = literal.descendantOfTypeStrict<RsPath>()?.textRange
                if (structNameRange != null) {
                    val annotation = holder.createErrorAnnotation(structNameRange, "Some fields are missing")
                    annotation.registerFix(AddStructFieldsFix(literal), body.parent.textRange)
                    annotation.registerFix(AddStructFieldsFix(literal, recursive = true), body.parent.textRange)
                }
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
        if (this !is RsParenExpr) return
        val fix = RemoveParenthesesFromExprIntention()
        if (fix.isAvailable(this))
            holder.createWeakWarningAnnotation(this, message)
                .registerFix(RemoveParenthesesFromExprIntention())
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

fun calculateMissingFields(expr: RsStructLiteralBody, decl: RsFieldsOwner): List<RsFieldDecl> {
    val declaredFields = expr.structLiteralFieldList.map { it.referenceName }.toSet()
    return decl.namedFields.filter { it.name !in declaredFields && !it.queryAttributes.hasCfgAttr() }
}
