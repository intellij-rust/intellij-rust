package org.rust.ide.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.appendMetaToList
import org.rust.lang.core.psi.util.parentOfType

class AddDerive : PsiElementBaseIntentionAction()
                , IntentionAction {
    override fun getFamilyName() = "Add derive clause"
    override fun getText() = "Add Derive Clause"
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val item = element.parentOfType<RustItem>() ?: return
        val existingDeriveAttr = item.outerAttrList.find { it.metaItem?.identifier?.textMatches("derive") ?: false }

        val deriveAttr = if (existingDeriveAttr != null) existingDeriveAttr
        else {
            val attr = RustElementFactory.createOuterAttr(project, "derive") ?: return
            when (item) {
                is RustStructItem -> item.addBefore(attr, item.struct)
                is RustEnumItem   -> item.addBefore(attr, item.enum)
                else              -> return
            } as RustOuterAttr
        }

        val newMetaItem = RustElementFactory.createMeta(project, "Trait") ?: return
        val appendedNewMetaItem = deriveAttr.appendMetaToList(newMetaItem) ?: return

        val marker = Object()
        PsiTreeUtil.mark(appendedNewMetaItem, marker)

        // XXX: currently reformat does not do anything
        val reformattedItem = CodeStyleManager.getInstance(project).reformat(item) as RustItem

        val reformattedNewMetaItem = PsiTreeUtil.releaseMark(reformattedItem, marker) as RustMetaItem

        editor?.caretModel?.moveToOffset(reformattedNewMetaItem.textOffset)
        editor?.selectionModel?.setSelection(
            reformattedNewMetaItem.textOffset,
            reformattedNewMetaItem.textOffset + reformattedNewMetaItem.textLength
        )
        reformattedNewMetaItem.delete()  // to remove the unnecessary "Trait" word
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        if (!element.isWritable) {
            return false
        }
        val item = element.parentOfType<RustItem>()
        return item is RustStructItem || item is RustEnumItem
    }

}

