package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBaseTypeElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustRefLikeTypeElement
import org.rust.lang.core.psi.util.parentOfType

/**
 * Set reference mutable
 *
 * ```
 * &type
 * ```
 *
 * to this:
 *
 * ```
 * &mut type
 * ```
 */
open class SetMutableIntention : RustElementBaseIntentionAction<SetMutableIntention.Context>() {
    override fun getText() = "Set reference mutable"
    override fun getFamilyName() = text

    open val mutable = true

    data class Context(
        val refType: RustRefLikeTypeElement,
        val baseType: RustBaseTypeElement
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val refType = element.parentOfType<RustRefLikeTypeElement>() ?: return null
        if (refType.and == null) return null
        val baseType = refType.type as? RustBaseTypeElement ?: return null
        if ((refType.mut == null) != mutable) return null
        return Context(refType, baseType)

    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val newType = RustPsiFactory(project).createReferenceType(ctx.baseType.text, mutable)
        ctx.refType.replace(newType)
    }
}
