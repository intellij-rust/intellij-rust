package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBlockExprElement
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustLambdaExprElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.util.parentOfType

class WrapLambdaExprIntention : RustElementBaseIntentionAction<RustExprElement>() {
    override fun getText() = "Add braces to lambda expression"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RustExprElement? {
        val lambdaExpr = element.parentOfType<RustLambdaExprElement>() ?: return null
        val body = lambdaExpr.expr ?: return null
        return if (body !is RustBlockExprElement) body else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RustExprElement) {
        val relativeCaretPosition = editor.caretModel.offset - ctx.textOffset

        val bodyStr = "\n${ctx.text}\n"
        val blockExpr = RustPsiFactory(project).createBlockExpr(bodyStr)

        val offset = ((ctx.replace(blockExpr)) as RustBlockExprElement).block?.expr?.textOffset ?: return
        editor.caretModel.moveToOffset(offset + relativeCaretPosition)
    }
}
