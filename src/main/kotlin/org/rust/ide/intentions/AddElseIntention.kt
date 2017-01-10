package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustIfExprElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.util.parentOfType

class AddElseIntention : RustElementBaseIntentionAction<RustIfExprElement>() {
    override fun getText() = "Add else branch to is if statement"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RustIfExprElement? {
        val ifStmnt = element.parentOfType<RustIfExprElement>() as? RustIfExprElement ?: return null
        return if (ifStmnt.elseBranch == null) ifStmnt else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RustIfExprElement) {
        val ifStmnt = ctx
        val ifExpr = RustPsiFactory(project).createExpression("${ifStmnt.text}\nelse {}") as RustIfExprElement
        val elseBlockOffset = (ifStmnt.replace(ifExpr) as RustIfExprElement).elseBranch?.block?.textOffset ?: return
        editor.caretModel.moveToOffset(elseBlockOffset + 1)
    }
}
