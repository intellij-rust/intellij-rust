package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.mixin.queryAttributes
import org.rust.lang.core.psi.util.parentOfType

class AddDerive : PsiElementBaseIntentionAction()
                , IntentionAction {
    override fun getFamilyName() = "Add derive clause"
    override fun getText() = "Add derive clause"
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val item = element.parentOfType<RustItem>() ?: return
        val keyword = getStructOrEnumKeyword(item) ?: return

        val deriveAttr = findOrCreateDeriveAttr(project, item, keyword) ?: return
        val reformattedDeriveAttr = reformat(project, item, deriveAttr)
        moveCaret(editor, reformattedDeriveAttr)
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!element.isWritable) {
            return false
        }
        val item = element.parentOfType<RustItem>() ?: return false
        return getStructOrEnumKeyword(item) != null
    }

    private fun getStructOrEnumKeyword(item: RustItem): PsiElement? = when (item) {
        is RustStructItem -> item.struct
        is RustEnumItem   -> item.enum
        else              -> null
    }

    private fun findOrCreateDeriveAttr(project: Project, item: RustItem, keyword: PsiElement): RustOuterAttr? {
        val existingDeriveAttr = item.queryAttributes.findOuterAttr("derive")
        if (existingDeriveAttr != null) {
            return existingDeriveAttr
        }

        val attr = RustElementFactory.createOuterAttr(project, "derive()") ?: return null
        return item.addBefore(attr, keyword) as RustOuterAttr
    }

    private fun reformat(project: Project, item: RustItem, deriveAttr: RustOuterAttr): RustOuterAttr {
        val marker = Object()
        PsiTreeUtil.mark(deriveAttr, marker)

        // XXX: currently reformat does not do anything
        val reformattedItem = CodeStyleManager.getInstance(project).reformat(item) as RustItem
        return PsiTreeUtil.releaseMark(reformattedItem, marker) as RustOuterAttr
    }

    private fun moveCaret(editor: Editor?, deriveAttr: RustOuterAttr) {
        val offset = deriveAttr.metaItem?.rparen?.textOffset ?:
            deriveAttr.rbrack?.textOffset ?:
            deriveAttr.textOffset
        editor?.caretModel?.moveToOffset(offset)
    }
}

