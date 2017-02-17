package org.rust.ide.core.overrideImplement

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.util.parentOfType

class ImplementMembersHandler: LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor, file: PsiFile): Boolean {
        if (file !is RsFile)
            return false
        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
        val classOrObject = elementAtCaret?.parentOfType<RsImplItem>(strict = false)
        return classOrObject != null 
    }

    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val elementAtCaret = file.findElementAt(editor.caretModel.offset)
        val implItem = elementAtCaret?.parentOfType<RsImplItem>(strict = false) ?: error("No impl trait item")
        generateTraitMembers(implItem)
    }
}