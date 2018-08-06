/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.operator

class FlipBinaryExpressionIntention : RsElementBaseIntentionAction<RsBinaryExpr>() {

    override fun getText(): String = "Flip binary expression"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsBinaryExpr? {
        val binaryExpr = element.ancestorStrict<RsBinaryExpr>() ?: return null
        if (binaryExpr.right == null) return null
        val op = binaryExpr.operator
        val opText = op.text
        text = when (op.elementType) {
            in COMMUNICATIVE_OPERATORS ->
                "Flip '$opText'"
            in CHANGE_SEMANTICS_OPERATORS ->
                "Flip '$opText' (changes semantics)"
            in COMPARISON_OPERATORS ->
                "Flip '$opText' to '${flippedOp(opText)}'"
            else ->
                return null
        }
        return binaryExpr
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsBinaryExpr) {
        val right = ctx.right?.text ?: return
        val left = ctx.left.text
        val op = ctx.operator.text?.let { flippedOp(it) }
        ctx.replace(RsPsiFactory(project).createExpression("$right $op $left"))
    }

    companion object {
        val COMMUNICATIVE_OPERATORS = listOf(PLUS, MUL, AND, OR, XOR, EQEQ, EXCLEQ)
        val CHANGE_SEMANTICS_OPERATORS = listOf(MINUS, DIV, REM, ANDAND, OROR, GTGT, LTLT)
        val COMPARISON_OPERATORS = listOf(GT, GTEQ, LT, LTEQ)

        fun flippedOp(op: String): String = when (op) {
            ">" -> "<"
            ">=" -> "<="
            "<" -> ">"
            "<=" -> ">="
            else -> op
        }
    }
}
