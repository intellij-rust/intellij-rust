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
import org.rust.lang.core.psi.ext.*

class FlipBinaryExpressionIntention : RsElementBaseIntentionAction<RsBinaryExpr>() {

    companion object {
        private val TARGET_OPERATIONS = setOf(PLUS, MUL, OROR, ANDAND, EQEQ, EXCLEQ, GT, GTEQ, LT, LTEQ)
    }
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsBinaryExpr? {
        val binaryExpr = element.ancestorStrict<RsBinaryExpr>() ?: return null
        if (binaryExpr.right == null) return null
        val op = binaryExpr.operator
        if (op.elementType !in TARGET_OPERATIONS) return null
        text = "Flip '${op.text}'"
        return binaryExpr
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsBinaryExpr) {
        val right = ctx.right?.text ?: return
        val left = ctx.left.text
        val op = ctx.operator.text?.let {
            when (it) {
                ">" -> "<"
                ">=" -> "<="
                "<" -> ">"
                "<=" -> ">="
                else -> it
            }
        }
        ctx.replace(RsPsiFactory(project).createExpression("$right $op $left"))
    }

}
