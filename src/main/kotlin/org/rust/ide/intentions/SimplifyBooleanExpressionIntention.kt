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
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.ancestors

class SimplifyBooleanExpressionIntention : RsElementBaseIntentionAction<RsExpr>() {
    override fun getText() = RsBundle.message("intention.name.simplify.boolean.expression")
    override fun getFamilyName() = RsBundle.message("intention.family.name.simplify.boolean.expression")

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsExpr? =
        element.ancestorStrict<RsExpr>()
            ?.ancestors
            ?.takeWhile { it is RsExpr }
            ?.map { it as RsExpr }
            ?.findLast { BooleanExprSimplifier.canBeSimplified(it) }
            ?.takeIf { PsiModificationUtil.canReplace(it) }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        val simplified = BooleanExprSimplifier(project).simplify(ctx) ?: return
        ctx.replace(simplified)
    }
}
