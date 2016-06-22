package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType

class AddDeriveIntention : PsiElementBaseIntentionAction() {
    override fun getFamilyName() = "Add derive clause"
    override fun getText() = "Add derive clause"
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val (item, keyword) = getTarget(element) ?: return
        val deriveAttr = findOrCreateDeriveAttr(project, item, keyword) ?: return
        val reformattedDeriveAttr = reformat(project, item, deriveAttr)
        moveCaret(editor, reformattedDeriveAttr)
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
        element.isWritable && getTarget(element) != null

    private fun getTarget(element: PsiElement): Pair<RustStructOrEnumItemElement, PsiElement>? {
        val item = element.parentOfType<RustStructOrEnumItemElement>() ?: return null
        val keyword = when (item) {
            is RustStructItemElement -> item.vis ?: item.struct
            is RustEnumItemElement -> item.vis ?: item.enum
            else -> null
        } ?: return null
        return item to keyword
    }

    private fun findOrCreateDeriveAttr(project: Project, item: RustStructOrEnumItemElement, keyword: PsiElement): RustOuterAttrElement? {
        val existingDeriveAttr = item.findOuterAttr("derive")
        if (existingDeriveAttr != null) {
            return existingDeriveAttr
        }

        val attr = RustElementFactory.createOuterAttr(project, "derive()") ?: return null
        return item.addBefore(attr, keyword) as RustOuterAttrElement
    }

    private fun reformat(project: Project, item: RustStructOrEnumItemElement, deriveAttr: RustOuterAttrElement): RustOuterAttrElement {
        val marker = Object()
        PsiTreeUtil.mark(deriveAttr, marker)
        val reformattedItem = CodeStyleManager.getInstance(project).reformat(item)
        return PsiTreeUtil.releaseMark(reformattedItem, marker) as RustOuterAttrElement
    }

    private fun moveCaret(editor: Editor, deriveAttr: RustOuterAttrElement) {
        val offset = deriveAttr.metaItem.rparen?.textOffset ?:
            deriveAttr.rbrack.textOffset ?:
            deriveAttr.textOffset
        editor.caretModel.moveToOffset(offset)
    }
}

