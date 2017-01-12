package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.util.parentOfType

class ImplementStructIntention : RsElementBaseIntentionAction<RsStructItem>() {
    override fun getText() = "Implement struct"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsStructItem?
        = element.parentOfType<RsStructItem>()

    override fun invoke(project: Project, editor: Editor, ctx: RsStructItem) {
        val identifier = ctx.identifier.text

        var impl = RustPsiFactory(project).createInherentImplItem(identifier)
        val file = ctx.containingFile

        impl = file.addAfter(impl, ctx) as? RsImplItem ?: return

        editor.caretModel.moveToOffset(impl.textOffset + impl.textLength - 1)
    }
}
