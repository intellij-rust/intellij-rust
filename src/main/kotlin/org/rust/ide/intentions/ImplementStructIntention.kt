package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType

class ImplementStructIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Implement struct"
    override fun getFamilyName() = text
    override fun startInWriteAction() = true

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val struct = element.parentOfType<RustStructItemElement>() ?: return
        val identifier = struct.identifier.text

        var impl = RustPsiFactory(project).createInherentImplItem(identifier)
        val file = element.parentOfType<PsiFile>() ?: return

        impl = file.addAfter(impl, struct) as? RustImplItemElement ?: return

        (editor ?: return).caretModel.moveToOffset(impl.textOffset + impl.textLength - 1)
    }

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return element.parentOfType<RustStructItemElement>() != null
    }
}
