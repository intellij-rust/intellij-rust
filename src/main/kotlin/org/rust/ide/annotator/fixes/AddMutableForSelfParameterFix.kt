package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*

open class AddMutableForSelfParameterFix(val parameter: RsSelfParameter) : LocalQuickFixAndIntentionActionOnPsiElement(parameter) {
    override fun getFamilyName(): String = text
    override fun getText(): String  = "Add mutable"
    open val mutable = true

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val newSelf = RsPsiFactory(project).createSelf(true)
        parameter.replace(newSelf)
    }

}
