/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.annotator.getFunctionCallContext
import org.rust.ide.fixes.RsQuickFixBase
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.RsWithMacrosInspectionVisitor
import org.rust.ide.utils.template.newTemplateBuilder
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.expandedStmtsAndTailExpr
import org.rust.lang.core.psi.ext.findFirstMetaItem
import org.rust.lang.core.types.implLookupAndKnownItems
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type

private fun RsExpr.returnsStdResult(): Boolean {
    val (_, knownItems) = implLookupAndKnownItems
    val type = type as? TyAdt ?: return false
    return type.item == knownItems.Result
}

private class FixAddLetUnderscore(anchor: RsExpr) : RsQuickFixBase<RsExpr>(anchor) {
    override fun getFamilyName() = RsBundle.message("inspection.UnusedMustUse.FixAddLetUnderscore.name")
    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        val letExpr = RsPsiFactory(project).createLetDeclaration("_", element)
        val newLetExpr = element.parent.replace(letExpr) as RsLetDecl
        val pat = newLetExpr.pat ?: return
        val tpl = editor?.newTemplateBuilder(newLetExpr) ?: return
        tpl.replaceElement(pat)
        tpl.runInline()
    }
}

private class FixAddUnwrap(element: RsExpr) : RsQuickFixBase<RsExpr>(element) {
    override fun getFamilyName() = RsBundle.message("inspection.UnusedMustUse.FixAddUnwrap.name")
    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        element.replace(RsPsiFactory(project).createExpression("${element.text}.unwrap()"))
    }
}

private class FixAddExpect(anchor: RsExpr) : RsQuickFixBase<RsExpr>(anchor) {
    override fun getFamilyName() = RsBundle.message("inspection.UnusedMustUse.FixAddExpect.family.name")
    override fun getText() = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        val dotExpr = RsPsiFactory(project).createExpression("${element.text}.expect(\"TODO: panic message\")")
        val newDotExpr = element.replace(dotExpr) as RsDotExpr
        val expectArgs = newDotExpr.methodCall?.valueArgumentList?.exprList
        val stringLiteral = expectArgs?.singleOrNull() as RsLitExpr
        val tpl = editor?.newTemplateBuilder(newDotExpr) ?: return
        val rangeWithoutQuotes = (stringLiteral.kind as? RsLiteralKind.String)?.offsets?.value ?: return
        tpl.replaceElement(stringLiteral, rangeWithoutQuotes, "TODO: panic message")
        tpl.runInline()
    }
}

private class InspectionResult(@InspectionMessage val description: String, val fixes: List<LocalQuickFix>)

private fun inspectAndProposeFixes(expr: RsExpr): InspectionResult? {
    val mustUseAttrName = "must_use"
    val type = expr.type as? TyAdt
    val func = when (expr) {
        is RsDotExpr -> expr.methodCall?.getFunctionCallContext()?.function
        is RsCallExpr -> expr.getFunctionCallContext()?.function
        else -> null
    }
    val attrType = type?.item?.findFirstMetaItem(mustUseAttrName)
    val attrFunc = func?.findFirstMetaItem(mustUseAttrName)
    val description = when {
        attrType != null -> RsBundle.message("inspection.UnusedMustUse.description.type.attribute", type)
        attrFunc != null -> RsBundle.message("inspection.UnusedMustUse.description.function.attribute", func.name.toString())
        else -> return null
    }
    val fixes: MutableList<LocalQuickFix> = mutableListOf(FixAddLetUnderscore(expr))
    if (expr.returnsStdResult()) {
        fixes += FixAddExpect(expr)
        fixes += FixAddUnwrap(expr)
    }
    return InspectionResult(description, fixes)
}

/** Analogue of rustc's unused_must_use. See also [RsDoubleMustUseInspection]. */
class RsUnusedMustUseInspection : RsLintInspection() {
    override fun getLint(element: PsiElement) = RsLint.UnusedMustUse

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsWithMacrosInspectionVisitor() {
        override fun visitExprStmt(o: RsExprStmt) {
            super.visitExprStmt(o)
            val parent = o.parent
            if (parent is RsBlock) {
                // Ignore if o is actually a tail expr
                val (_, tailExpr) = parent.expandedStmtsAndTailExpr
                if (o.expr == tailExpr) return
            }
            val problem = inspectAndProposeFixes(o.expr)
            if (problem != null) {
                holder.registerLintProblem(o.expr, problem.description, RsLintHighlightingType.WEAK_WARNING, problem.fixes)
            }
        }
    }
}
