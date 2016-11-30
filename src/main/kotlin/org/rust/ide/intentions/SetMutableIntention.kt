package org.rust.ide.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementFactory
import org.rust.lang.core.psi.RustParameterElement
import org.rust.lang.core.psi.RustPathTypeElement
import org.rust.lang.core.psi.RustRefTypeElement
import org.rust.lang.core.psi.util.parentOfType

/**
 * Set reference mutable in a parameter of a function declaration
 *
 * ```
 * fn func(param: &type)
 * ```
 *
 * to this:
 *
 * ```
 * fn func(param: &mut type)
 * ```
 */
open class SetMutableIntention : PsiElementBaseIntentionAction() {
    override fun getText() = "Set reference mutable"
    override fun getFamilyName() = text
    override fun startInWriteAction() = true

    open val mutable = true

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val item = element.parentOfType<RustParameterElement>() ?: return false
        val type = (item.type ?: return false) as? RustRefTypeElement ?: return false

        return (type.mut == null) == mutable
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val item = element.parentOfType<RustParameterElement>() ?: return
        val ref = (item.type ?: return) as? RustRefTypeElement ?: return
        val pat = item.pat ?: return
        val path = ref.type as? RustPathTypeElement ?: return

        val newType = RustElementFactory.createParameter(project, "${pat.text}: &${if (mutable) "mut " else ""}${path.text}") ?: return
        item.replace(newType)
    }
}
