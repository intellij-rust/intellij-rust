package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType

class AddDeriveIntention : RsElementBaseIntentionAction<AddDeriveIntention.Context>() {
    override fun getFamilyName() = "Add derive clause"
    override fun getText() = "Add derive clause"

    class Context(
        val item: RustStructOrEnumItemElement,
        val itemStart: PsiElement
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val item = element.parentOfType<RustStructOrEnumItemElement>() ?: return null
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

    private fun findOrCreateDeriveAttr(project: Project, item: RustStructOrEnumItemElement, keyword: PsiElement): RsOuterAttr {
        val existingDeriveAttr = item.findOuterAttr("derive")
        if (existingDeriveAttr != null) {
            return existingDeriveAttr
        }

        val attr = RustPsiFactory(project).createOuterAttr("derive()")
        return item.addBefore(attr, keyword) as RsOuterAttr
    }

    private fun reformat(project: Project, item: RustStructOrEnumItemElement, deriveAttr: RsOuterAttr): RsOuterAttr {
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

