/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.utils.BooleanExprSimplifier
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.ancestors

class SimplifyBooleanExpressionIntention : RsElementBaseIntentionAction<RsExpr>() {
    override fun getText() = RsBundle.message("intention.Rust.SimplifyBooleanExpression.text")
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsExpr? =
        element.ancestorStrict<RsExpr>()
            ?.ancestors
            ?.takeWhile { it is RsExpr }
            ?.map { it as RsExpr }
            ?.findLast { BooleanExprSimplifier.canBeSimplified(it) }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        val simplified = BooleanExprSimplifier(project).simplify(ctx) ?: return
        ctx.replace(simplified)
    }
}
