package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustPathTypeElement
import org.rust.lang.core.psi.RustPsiFactory
import org.rust.lang.core.psi.RustRefTypeElement
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

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val ref = element.parentOfType<RustRefTypeElement>() ?: return false

        return (ref.mut == null) == mutable
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val ref = element.parentOfType<RustRefTypeElement>() ?: return
        val path = ref.type as? RustPathTypeElement ?: return

        val newType = RustPsiFactory(project).createReferenceType(path.text, mutable)
        ref.replace(newType)
    }
}
