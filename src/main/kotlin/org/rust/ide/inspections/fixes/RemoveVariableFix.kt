/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.topLevelPattern


/**
 * Fix that removes a variable.
 * A heuristic is used whether to also remove its expression or not.
 */
class RemoveVariableFix(binding: RsPatBinding, private val bindingName: String) : LocalQuickFixOnPsiElement(binding) {
    override fun getText() = "Remove variable `${bindingName}`"
    override fun getFamilyName() = "Remove variable"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val binding = startElement as? RsPatBinding ?: return
        val patIdent = binding.topLevelPattern as? RsPatIdent ?: return
        deleteVariable(patIdent)
    }
}

private fun deleteVariable(pat: RsPatIdent) {
    val decl = pat.parentOfType<RsLetDecl>() ?: return
    val expr = decl.expr

    if (expr != null && expr.hasSideEffects) {
        val factory = RsPsiFactory(expr.project)
        val newExpr = if (decl.semicolon != null) {
            factory.tryCreateExprStmt(expr.text) ?: expr
        } else {
            expr
        }
        decl.replace(newExpr)
    } else {
        decl.delete()
    }
}

private val RsExpr.hasSideEffects: Boolean
    get() = when (this) {
        is RsUnitExpr, is RsLitExpr, is RsPathExpr -> false
        is RsParenExpr -> expr?.hasSideEffects ?: false
        is RsCastExpr -> expr.hasSideEffects
        is RsDotExpr -> expr.hasSideEffects || methodCall != null
        is RsTupleExpr -> exprList.any { it.hasSideEffects }
        is RsStructLiteral -> structLiteralBody.structLiteralFieldList.any { it.expr?.hasSideEffects ?: false }
        is RsBinaryExpr -> exprList.any { it.hasSideEffects }
        is RsUnaryExpr -> expr?.hasSideEffects ?: false
        else -> true
    }
