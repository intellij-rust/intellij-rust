package org.rust.ide.navigation.goto

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustFile
import org.rust.lang.core.psi.util.parentOfType

class RustGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun startInWriteAction() = false

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val focusedElement = file.findElementAt(editor.caretModel.offset) ?: return

        val focusedMod = focusedElement.parentOfType<RustModItem>() ?: return
        val superMod = focusedMod.`super` ?: return

        superMod.navigate(true)
    }

    override fun isValidFor(editor: Editor?, file: PsiFile?) = file is RustFile
}
