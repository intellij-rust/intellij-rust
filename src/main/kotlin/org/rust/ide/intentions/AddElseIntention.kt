package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustIfExprElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.util.parentOfType

class AddElseIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Add else branch to is if statement"
    override fun getFamilyName(): String = text
    override fun startInWriteAction() = true

    private data class Context(
        val blockExpr: RustIfExprElement
    )

    private fun findContext(element: PsiElement): Context? {
        if (!element.isWritable) return null

        val ifStmnt = element.parentOfType<RustIfExprElement>() as? RustIfExprElement ?: return null
        return if (ifStmnt.elseBranch == null) Context(ifStmnt) else null
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        findContext(element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val ifStmnt = findContext(element)?.blockExpr ?: return
        val ifExpr = RustPsiFactory(project).createExpression("${ifStmnt.text}\nelse {}") as RustIfExprElement
        val elseBlockOffset = (ifStmnt.replace(ifExpr) as RustIfExprElement).elseBranch?.block?.textOffset ?: return
        editor?.caretModel?.moveToOffset(elseBlockOffset + 1) ?: return
    }
}
