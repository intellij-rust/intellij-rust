package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.util.parentOfType

class ImplementStructIntention : RustElementBaseIntentionAction<RustStructItemElement>() {
    override fun getText() = "Implement struct"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RustStructItemElement?
        = element.parentOfType<RustStructItemElement>()

    override fun invoke(project: Project, editor: Editor, ctx: RustStructItemElement) {
        val identifier = ctx.identifier.text

        var impl = RustPsiFactory(project).createInherentImplItem(identifier)
        val file = ctx.containingFile

        impl = file.addAfter(impl, ctx) as? RustImplItemElement ?: return

        editor.caretModel.moveToOffset(impl.textOffset + impl.textLength - 1)
    }
}
