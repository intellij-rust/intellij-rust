/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsMatchArmGuard
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.parentMatchArm

class MoveGuardToMatchArmIntention : RsElementBaseIntentionAction<MoveGuardToMatchArmIntention.Context>() {
    override fun getText(): String = "Move guard inside the match arm"
    override fun getFamilyName(): String = text

    data class Context(
        val guard: RsMatchArmGuard,
        val guardExpr: RsExpr,
        val armBody: RsExpr
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val guard = element.ancestorStrict<RsMatchArmGuard>() ?: return null
        if (guard.let != null) return null // TODO: support `if let guard` syntax
        val guardExpr = guard.expr ?: return null
        val armBody = guard.parentMatchArm.expr ?: return null
        return Context(guard, guardExpr, armBody)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (guard, guardExpr, oldBody) = ctx
        val caretOffsetInGuard = editor.caretModel.offset - guard.textOffset
        val psiFactory = RsPsiFactory(project)
        var newBody = psiFactory.createIfExpression(guardExpr, oldBody)
        newBody = oldBody.replace(newBody) as RsIfExpr
        guard.delete()
        editor.caretModel.moveToOffset(newBody.textOffset + caretOffsetInGuard)
    }
}
