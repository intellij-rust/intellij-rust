/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.descendantOfTypeOrSelf
import org.rust.lang.core.psi.ext.parentMatchArm
import org.rust.openapiext.moveCaretToOffset

class MoveGuardToMatchArmIntention : RsElementBaseIntentionAction<MoveGuardToMatchArmIntention.Context>() {
    override fun getText(): String = RsBundle.message("intention.name.move.guard.inside.match.arm")
    override fun getFamilyName(): String = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    data class Context(
        val guard: RsMatchArmGuard,
        val guardExpr: RsExpr,
        val armBody: RsExpr
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val guard = element.ancestorStrict<RsMatchArmGuard>() ?: return null
        if (guard.expr?.descendantOfTypeOrSelf<RsLetExpr>() != null) return null // TODO: support `if let guard` syntax
        val guardExpr = guard.expr ?: return null
        val armBody = guard.parentMatchArm.expr ?: return null
        if (!PsiModificationUtil.canReplaceAll(guard, armBody)) return null
        return Context(guard, guardExpr, armBody)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (guard, guardExpr, oldArmBody) = ctx
        val caretOffsetInGuard = editor.caretModel.offset - guard.textOffset
        val psiFactory = RsPsiFactory(project)
        var newArmBody = psiFactory.createIfExpression(guardExpr, oldArmBody)
        newArmBody = oldArmBody.replace(newArmBody) as RsIfExpr
        guard.delete()
        editor.moveCaretToOffset(newArmBody, newArmBody.textOffset + caretOffsetInGuard)
    }
}
