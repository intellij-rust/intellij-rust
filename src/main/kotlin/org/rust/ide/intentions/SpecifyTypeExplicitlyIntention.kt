package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPatIdent
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.types.infer.inferDeclarationType
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnknown


class SpecifyTypeExplicitlyIntention : RsElementBaseIntentionAction<SpecifyTypeExplicitlyIntention.Context>() {
    override fun getFamilyName() = "Specify type explicitly"

    override fun getText() = "Specify type explicitly"

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val letDecl = element.parentOfType<RsLetDecl>() ?: return null
        if(letDecl.typeReference != null) {
            return null
        }
        val ident = letDecl.pat as? RsPatIdent ?: return null
        val type: Ty = inferDeclarationType(ident.patBinding)
        if (type is TyUnknown) {
            return null
        }
        return Context(type, letDecl)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val factory = RsPsiFactory(project)
        val createdType = factory.createType(ctx.type.toString())
        val letDecl = ctx.letDecl
        val colon = letDecl.addAfter(factory.createColon(), letDecl.pat)
        colon.parent.addAfter(createdType, colon)
    }


    data class Context(
        val type: Ty,
        val letDecl: RsLetDecl
    )
}
