/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.ide.intentions.util.macros.InvokeInside
import org.rust.ide.refactoring.introduceVariable.extractExpression
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

class IntroduceLocalVariableIntention : RsElementBaseIntentionAction<RsExpr>() {
    override fun getText(): String = RsBundle.message("intention.name.introduce.local.variable")
    override fun getFamilyName(): String = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsExpr? {
        val expr = element.ancestors
            .takeWhile {
                it !is RsBlock && it !is RsMatchExpr && it !is RsLambdaExpr
            }
            .find {
                val parent = it.parent
                parent is RsRetExpr
                    || parent is RsExprStmt
                    || parent is RsMatchArm && it == parent.expr
                    || parent is RsLambdaExpr && it == parent.expr
            } as? RsExpr
            ?: return null

        if (expr.type is TyUnit) return null
        if (!PsiModificationUtil.canReplace(expr)) return null

        return expr
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        extractExpression(
            editor, ctx, postfixLet = false, RsBundle.message("command.name.introduce.local.variable")
        )
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY
}
