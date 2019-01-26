/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.ext.*

class ExtractNestedBlockIntention : RsElementBaseIntentionAction<RsBlockExpr>() {
    override fun getText(): String = "Extract content from nested block"
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsBlockExpr? {
        val blockExpr = element.ancestorOrSelf<RsBlockExpr>() ?: return null
        val parent = blockExpr.parent

        return if (parent is RsBlock ||
            parent is RsExprStmt && parent.parent is RsBlock) blockExpr else null
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsBlockExpr) {
        val block = ctx.block
        val holder = ctx.parent as? RsExprStmt ?: ctx
        val parent = holder.parent ?: return

        val elements = block.childrenWithLeaves
            .drop(1)
            .toMutableList()
            .dropLast(1)

        for (elem in elements) {
            parent.addBefore(elem, holder)
        }

        holder.delete()
        parent.let {
            it.replace(CodeStyleManager.getInstance(project).reformat(it))
        }
    }
}
