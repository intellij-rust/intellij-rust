package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.ext.parentOfType

class AddMutableIntention : RemoveMutableIntention(){
    override fun getText() = "Add mutable"
    override val mutable = false

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val letDecl = element.parentOfType<RsLetDecl>() ?: return null
        if (isMutable(letDecl) != mutable) return null
        return Context(letDecl)
    }
}
