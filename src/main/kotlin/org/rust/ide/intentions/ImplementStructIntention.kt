package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.util.parentOfType

class ImplementStructIntention : RustElementBaseIntentionAction() {
    override fun getText() = "Implement struct"
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean =
        element.parentOfType<RustStructItemElement>() != null

    override fun invokeImpl(project: Project, editor: Editor, element: PsiElement) {
        val struct = element.parentOfType<RustStructItemElement>() ?: return
        val identifier = struct.identifier.text

        var impl = RustPsiFactory(project).createInherentImplItem(identifier)
        val file = element.parentOfType<PsiFile>() ?: return

        impl = file.addAfter(impl, struct) as? RustImplItemElement ?: return

        (editor ?: return).caretModel.moveToOffset(impl.textOffset + impl.textLength - 1)
    }
}
