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
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByReference
import org.rust.lang.core.psi.ext.RsBindingModeKind.BindByValue
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.type

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
            val declaration = expr.declaration as? RsNamedElement ?: return null

            return when {
                declaration is RsSelfParameter -> {
                    AddMutableFix(declaration)
                }

                declaration is RsPatBinding && declaration.kind is BindByValue
                    && (declaration.isArg || expr.type !is TyReference) -> {
                    AddMutableFix(declaration)
                }

                else -> null
            }
        }
    }
}

fun updateMutable(project: Project, binding: RsNamedElement, mutable: Boolean = true) {
    when (binding) {
        is RsPatBinding -> {
            val parameter = binding.ancestorStrict<RsValueParameter>()
            val type = parameter?.typeReference?.skipParens()
            if (type is RsRefLikeType) {
                val typeReference = type.typeReference ?: return
                val newParameterExpr = RsPsiFactory(project)
                    .createValueParameter(parameter.pat?.text!!, typeReference, mutable, lifetime = type.lifetime)
                parameter.replace(newParameterExpr)
                return
            }
            val isRef = binding.kind is BindByReference
            val newPatBinding = RsPsiFactory(project).createPatBinding(binding.identifier.text, mutable, isRef)
            binding.replace(newPatBinding)
        }
        is RsSelfParameter -> {
            val newSelf: RsSelfParameter = if (binding.isRef) {
                RsPsiFactory(project).createSelfReference(true)
            } else {
                RsPsiFactory(project).createSelf(true)
            }
            binding.replace(newSelf)
        }
    }
}
