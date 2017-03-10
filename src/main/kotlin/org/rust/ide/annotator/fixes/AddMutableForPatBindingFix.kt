package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.parentOfType

open class AddMutableForPatBindingFix(val binding: RsPatBinding) : LocalQuickFixAndIntentionActionOnPsiElement(binding) {
    override fun getFamilyName(): String = text
    override fun getText(): String  = "Add mutable"
    open val mutable = true

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        UpdateMutable(project, binding, mutable)
    }
}

fun UpdateMutable(project: Project, binding: RsPatBinding, mutable: Boolean = true) {
    val parameter = binding.parentOfType<RsValueParameter>()
    val ref = parameter?.typeReference
    if (ref is RsRefLikeType) {
        val newParameterExpr = RsPsiFactory(project)
            .createValueParameter(parameter?.pat?.text!!, ref.typeReference!!, mutable)
        parameter?.replace(newParameterExpr)
        return
    }
    val newPatBinding = RsPsiFactory(project).createPatBinding(binding.identifier.text, mutable)
    binding.replace(newPatBinding)
}
