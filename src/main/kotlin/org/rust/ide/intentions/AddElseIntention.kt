/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict

class AddElseIntention : RsElementBaseIntentionAction<RsIfExpr>() {

    override fun getText(): String = "Add else branch to this if statement"

    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsIfExpr? {
        val ifStmt = element.ancestorStrict<RsIfExpr>() ?: return null
        return if (ifStmt.elseBranch == null) ifStmt else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsIfExpr) {
        val ifStmt = ctx
        val ifExpr = RsPsiFactory(project).createExpression("${ifStmt.text}\nelse {}") as RsIfExpr
        val elseBlockOffset = (ifStmt.replace(ifExpr) as RsIfExpr).elseBranch?.block?.textOffset ?: return
        editor.caretModel.moveToOffset(elseBlockOffset + 1)
    }
}
