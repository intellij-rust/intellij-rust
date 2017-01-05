package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
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
open class SetMutableIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Set reference mutable"
    override fun getFamilyName() = text
    override fun startInWriteAction() = true

    open val mutable = true

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean =
        findContext(element) != null

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val ctx = findContext(element) ?: return


        val newType = RustPsiFactory(project).createReferenceType(ctx.baseType.text, mutable)
        ctx.refType.replace(newType)
    }

    private data class Context(
        val refType: RustRefLikeTypeElement,
        val baseType: RustBaseTypeElement
    )

    private fun findContext(element: PsiElement): Context? {
        val refType = element.parentOfType<RustRefLikeTypeElement>() ?: return null
        if (refType.and == null) return null
        val baseType = refType.type as? RustBaseTypeElement ?: return null
        if ((refType.mut == null) != mutable) return null
        return Context(refType, baseType)
    }
}
