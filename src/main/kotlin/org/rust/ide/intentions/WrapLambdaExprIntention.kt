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
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.syntaxTailStmt
import org.rust.openapiext.moveCaretToOffset

class WrapLambdaExprIntention : RsElementBaseIntentionAction<RsExpr>() {
    override fun getText() = RsBundle.message("intention.name.add.braces.to.lambda.expression")
    override fun getFamilyName() = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsExpr? {
        val lambdaExpr = element.ancestorStrict<RsLambdaExpr>() ?: return null
        val body = lambdaExpr.expr ?: return null
        if (body is RsBlockExpr) return null
        if (!PsiModificationUtil.canReplace(body)) return null

        return body
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsExpr) {
        val relativeCaretPosition = editor.caretModel.offset - ctx.textOffset

        val bodyStr = "\n${ctx.text}\n"
        val blockExpr = RsPsiFactory(project).createBlockExpr(bodyStr)

        val insertedBlock = (ctx.replace(blockExpr)) as RsBlockExpr
        val offset = insertedBlock.block.syntaxTailStmt?.textOffset ?: return
        editor.moveCaretToOffset(insertedBlock, offset + relativeCaretPosition)
    }
}
