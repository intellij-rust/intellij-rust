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
import org.rust.ide.utils.PsiInsertionPlace
import org.rust.lang.core.psi.RsIfExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.moveCaretToOffset

class AddElseIntention : RsElementBaseIntentionAction<PsiInsertionPlace>() {
    override fun getText() = RsBundle.message("intention.name.add.else.branch.to.this.if.statement")
    override fun getFamilyName(): String = text

    override val attributeMacroHandlingStrategy: InvokeInside get() = InvokeInside.MACRO_CALL

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): PsiInsertionPlace? {
        val ifExpr = element.ancestorStrict<RsIfExpr>() ?: return null
        if (ifExpr.elseBranch != null) return null
        val block = ifExpr.block ?: return null
        val rbrace = block.rbrace ?: return null
        if (element.startOffset >= block.lbrace.endOffset && element != rbrace) return null
        return PsiInsertionPlace.after(block)
    }

    override fun invoke(project: Project, editor: Editor, ctx: PsiInsertionPlace) {
        val newIfExpr = RsPsiFactory(project).createExpression("if a {} else {}") as RsIfExpr
        val insertedElseBlock = ctx.insert(newIfExpr.elseBranch!!)
        val elseBlockOffset = insertedElseBlock.block?.textOffset ?: return
        editor.moveCaretToOffset(insertedElseBlock, elseBlockOffset + 1)
    }
}
