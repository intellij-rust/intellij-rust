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
        val existingDeriveAttr = item.queryAttributes.findOuterAttr("derive")

        val deriveAttr = if (existingDeriveAttr != null) existingDeriveAttr
        else {
            val attr = RustElementFactory.createOuterAttr(project, "derive()") ?: return
            item.addBefore(attr, getKeywordItem(item) ?: return) as RustOuterAttr
        }

        val marker = Object()
        PsiTreeUtil.mark(deriveAttr, marker)

        // XXX: currently reformat does not do anything
        val reformattedItem = CodeStyleManager.getInstance(project).reformat(item) as RustItem
        val reformattedDeriveAttr = PsiTreeUtil.releaseMark(reformattedItem, marker) as RustOuterAttr

        val offset = reformattedDeriveAttr.metaItem?.rparen?.textOffset ?:
            reformattedDeriveAttr.rbrack?.textOffset ?:
            reformattedDeriveAttr.textOffset
        editor?.caretModel?.moveToOffset(offset)
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!element.isWritable) {
            return false
        }
        return getKeywordItem(element.parentOfType<RustItem>() ?: return false) != null
    }

    private fun getKeywordItem(item: RustItem): PsiElement? = when (item) {
        is RustStructItem -> item.struct
        is RustEnumItem   -> item.enum
        else              -> null
    }
}

