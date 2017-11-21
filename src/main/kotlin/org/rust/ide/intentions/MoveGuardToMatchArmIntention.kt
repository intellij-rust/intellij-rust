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
import org.rust.lang.core.psi.ext.parentMatchArm
import org.rust.lang.core.psi.ext.ancestorStrict

class MoveGuardToMatchArmIntention : RsElementBaseIntentionAction<MoveGuardToMatchArmIntention.Context>() {
    override fun getText(): String = "Move guard inside the match arm"
    override fun getFamilyName(): String = text

    data class Context(
        val guard: RsMatchArmGuard,
        val armBody: RsExpr
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val guard = element.ancestorStrict<RsMatchArmGuard>() ?: return null
        val armBody = guard.parentMatchArm.expr ?: return null
        return Context(guard, armBody)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (guard, oldBody) = ctx
        val caretOffsetInGuard = editor.caretModel.offset - guard.textOffset
        val psiFactory = RsPsiFactory(project)
        var newBody = psiFactory.createIfExpression(guard.expr, oldBody)
        newBody = oldBody.replace(newBody) as RsIfExpr
        guard.delete()
        editor.caretModel.moveToOffset(newBody.textOffset + caretOffsetInGuard)
    }
}
