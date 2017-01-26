package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsStructItem
import org.rust.lang.core.psi.util.parentOfType

class ImplementStructIntention : RsElementBaseIntentionAction<ImplementStructIntention.Context>() {
    override fun getText() = "Implement struct"
    override fun getFamilyName() = text

    class Context(val struct: RsStructItem, val name: String)

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val struct = element.parentOfType<RsStructItem>() ?: return null
        val name = struct.name ?: return null
        return Context(struct, name)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        var impl = RsPsiFactory(project).createInherentImplItem(ctx.name)

        impl = ctx.struct.parent.addAfter(impl, ctx.struct) as RsImplItem

        editor.caretModel.moveToOffset(impl.textOffset + impl.textLength - 1)
    }
}
