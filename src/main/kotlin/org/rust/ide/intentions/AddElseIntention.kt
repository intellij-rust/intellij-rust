package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.util.parentOfType

class AddElseIntention : RustElementBaseIntentionAction<RsIfExpr>() {
    override fun getText() = "Add else branch to is if statement"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsIfExpr? {
        val ifStmnt = element.parentOfType<RsIfExpr>() as? RsIfExpr ?: return null
        return if (ifStmnt.elseBranch == null) ifStmnt else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsIfExpr) {
        val ifStmnt = ctx
        val ifExpr = RustPsiFactory(project).createExpression("${ifStmnt.text}\nelse {}") as RsIfExpr
        val elseBlockOffset = (ifStmnt.replace(ifExpr) as RsIfExpr).elseBranch?.block?.textOffset ?: return
        editor.caretModel.moveToOffset(elseBlockOffset + 1)
    }
}
