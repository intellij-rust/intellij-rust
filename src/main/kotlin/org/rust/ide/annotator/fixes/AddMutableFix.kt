package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.types.declaration

class AddMutableFix(val binding: RsNamedElement) : LocalQuickFixAndIntentionActionOnPsiElement(binding) {
    override fun getFamilyName(): String = "Make mutable"
    override fun getText(): String = "Make `${binding.name}` mutable"
    val mutable = true

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        updateMutable(project, binding, mutable)
    }

    companion object {
        fun createIfCompatible(expr: RsExpr): AddMutableFix? {
            val declaration = expr.declaration
            return if (declaration is RsSelfParameter || declaration is RsPatBinding) {
                AddMutableFix(declaration as RsNamedElement)
            } else {
                null
            }
        }
    }
}

fun updateMutable(project: Project, binding: RsNamedElement, mutable: Boolean = true) {
    when (binding) {
        is RsPatBinding -> {
            val tuple = binding.parentOfType<RsPatTup>()
            val parameter = binding.parentOfType<RsValueParameter>()
            if (tuple != null && parameter != null) {
                return
            }

            val ref = parameter?.typeReference
            if (ref is RsRefLikeType) {
                val newParameterExpr = RsPsiFactory(project)
                    .createValueParameter(parameter.pat?.text!!, ref.typeReference!!, mutable)
                parameter.replace(newParameterExpr)
                return
            }
            val newPatBinding = RsPsiFactory(project).createPatBinding(binding.identifier.text, mutable)
            binding.replace(newPatBinding)
        }
        is RsSelfParameter -> {
            val newSelf = RsPsiFactory(project).createSelf(true)
            binding.replace(newSelf)
        }
    }
}
