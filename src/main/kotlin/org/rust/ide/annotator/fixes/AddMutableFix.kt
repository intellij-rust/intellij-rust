/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.typeElement
import org.rust.lang.core.types.declaration

class AddMutableFix(binding: RsNamedElement) : LocalQuickFixAndIntentionActionOnPsiElement(binding) {
    private val _text = "Make `${binding.name}` mutable"
    override fun getFamilyName(): String = "Make mutable"
    override fun getText(): String = _text
    val mutable = true

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        updateMutable(project, startElement as RsNamedElement, mutable)
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
            val tuple = binding.ancestorStrict<RsPatTup>()
            val parameter = binding.ancestorStrict<RsValueParameter>()
            if (tuple != null && parameter != null) {
                return
            }

            val type = parameter?.typeReference?.typeElement
            if (type is RsRefLikeType) {
                val newParameterExpr = RsPsiFactory(project)
                    .createValueParameter(parameter.pat?.text!!, type.typeReference, mutable)
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
