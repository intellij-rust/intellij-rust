/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.utils.simplifyBooleanExpression
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.ancestorStrict

class SimplifyBooleanExpressionIntention : RsElementBaseIntentionAction<RsExpr>() {
    override fun getText() = "Simplify boolean expression"
    override fun getFamilyName() = "Simplify boolean expression"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsExpr? =
        element.ancestorStrict<RsExpr>()
            ?.ancestors
            ?.takeWhile { it is RsExpr }
            ?.map { it as RsExpr }
            ?.findLast { isSimplifiableExpression(it) }

    private fun isSimplifiableExpression(psi: RsExpr): Boolean {
        return (psi.copy() as RsExpr).simplifyBooleanExpression().second
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        val (expr, isSimplified) = ctx.simplifyBooleanExpression()
        if (isSimplified)
            ctx.replace(expr)
    }
}
