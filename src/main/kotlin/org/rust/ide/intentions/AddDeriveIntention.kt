/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsStructOrEnumItemElement
import org.rust.lang.core.psi.ext.findOuterAttr
import org.rust.lang.core.psi.ext.ancestorStrict

class AddDeriveIntention : RsElementBaseIntentionAction<AddDeriveIntention.Context>() {
    override fun getFamilyName() = "Add derive clause"
    override fun getText() = "Add derive clause"

    class Context(
        val item: RsStructOrEnumItemElement,
        val itemStart: PsiElement
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val item = element.ancestorStrict<RsStructOrEnumItemElement>() ?: return null
        val keyword = when (item) {
            is RsStructItem -> item.vis ?: item.struct
            is RsEnumItem -> item.vis ?: item.enum
            else -> null
        } ?: return null
        return Context(item, keyword)

    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val deriveAttr = findOrCreateDeriveAttr(project, ctx.item, ctx.itemStart)
        val reformattedDeriveAttr = reformat(project, ctx.item, deriveAttr)
        moveCaret(editor, reformattedDeriveAttr)

    }

    private fun findOrCreateDeriveAttr(project: Project, item: RsStructOrEnumItemElement, keyword: PsiElement): RsOuterAttr {
        val existingDeriveAttr = item.findOuterAttr("derive")
        if (existingDeriveAttr != null) {
            return existingDeriveAttr
        }

        val attr = RsPsiFactory(project).createOuterAttr("derive()")
        return item.addBefore(attr, keyword) as RsOuterAttr
    }

    private fun reformat(project: Project, item: RsStructOrEnumItemElement, deriveAttr: RsOuterAttr): RsOuterAttr {
        val marker = Object()
        PsiTreeUtil.mark(deriveAttr, marker)
        val reformattedItem = CodeStyleManager.getInstance(project).reformat(item)
        return PsiTreeUtil.releaseMark(reformattedItem, marker) as RsOuterAttr
    }

    private fun moveCaret(editor: Editor, deriveAttr: RsOuterAttr) {
        val offset = deriveAttr.metaItem.metaItemArgs?.rparen?.textOffset ?:
            deriveAttr.rbrack.textOffset ?:
            deriveAttr.textOffset
        editor.caretModel.moveToOffset(offset)
    }
}

