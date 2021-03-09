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
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset

class AddElseIntention : RsElementBaseIntentionAction<RsIfExpr>() {
    override fun getText() = "Add else branch to this if statement"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsIfExpr? {
        val ifExpr = element.ancestorStrict<RsIfExpr>() ?: return null
        if (ifExpr.elseBranch != null) return null
        val block = ifExpr.block ?: return null
        if (element.startOffset >= block.lbrace.endOffset && element != block.rbrace) return null
        return ifExpr
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsIfExpr) {
        val newIfExpr = RsPsiFactory(project).createExpression("${ctx.text}\nelse {}") as RsIfExpr
        val elseBlockOffset = (ctx.replace(newIfExpr) as RsIfExpr).elseBranch?.block?.textOffset ?: return
        editor.caretModel.moveToOffset(elseBlockOffset + 1)
    }
}
