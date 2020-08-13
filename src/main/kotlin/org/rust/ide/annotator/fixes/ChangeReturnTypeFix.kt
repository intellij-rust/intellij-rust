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
import com.intellij.psi.util.PsiTreeUtil
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.presentation.render
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.ide.utils.import.RsImportHelper.importTypeReferencesFromTy
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.Ty
import org.rust.lang.core.types.ty.TyUnit


class ChangeReturnTypeFix(
    element: RsElement,
    private val actualTy: Ty
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    private val _text: String = run {
            val (item, name) = when (val callable = findCallableOwner(element)) {
                is RsFunction -> {
                    val item = if (callable.owner.isImplOrTrait) " of method" else " of function"
                    val name = callable.name?.let { " '$it'" } ?: ""
                    item to name
                }
                is RsLambdaExpr -> " of closure" to ""
                else -> "" to ""
            }
            "Change return type$item$name to '${actualTy.render(useAliasNames = true)}'"
        }

    override fun getText(): String = _text
    override fun getFamilyName(): String = "Change return type"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val owner = findCallableOwner(startElement) ?: return

        val oldRetType = when (owner) {
            is RsFunction -> owner.retType
            is RsLambdaExpr -> owner.retType
            else -> return
        }

        if (actualTy is TyUnit) {
            oldRetType?.delete()
            return
        }

        val psiFactory = RsPsiFactory(project)
        val text = actualTy.renderInsertionSafe(includeLifetimeArguments = true, useAliasNames = true)
        val retType = psiFactory.createRetType(text)

        if (oldRetType != null) {
            oldRetType.replace(retType)
        } else {
            val parameters = when (owner) {
                is RsFunction -> owner.valueParameterList
                is RsLambdaExpr -> owner.valueParameterList
                else -> return
            }
            owner.addAfter(retType, parameters)
        }

        importTypeReferencesFromTy(owner, actualTy, useAliases = true)
    }

    companion object {
        private fun findCallableOwner(element: PsiElement): RsElement? =
            PsiTreeUtil.getContextOfType(element, true, RsFunction::class.java, RsLambdaExpr::class.java)

        fun createIfCompatible(element: RsElement, actualTy: Ty): ChangeReturnTypeFix? {
            if (element.containingCrate?.origin != PackageOrigin.WORKSPACE) return null

            val owner = findCallableOwner(element)
            val isOverriddenFn = owner is RsFunction && owner.superItem != null
            if (isOverriddenFn) return null // TODO: Support overridden items

            if (owner is RsLambdaExpr && owner.retType == null) {
                return null
            }

            val retExpr = when (owner) {
                is RsFunction -> owner.block?.expr
                is RsLambdaExpr -> owner.expr
                else -> return null
            }

            val isRetExpr = element.parent is RsRetExpr || retExpr === element
            if (!isRetExpr) return null

            return ChangeReturnTypeFix(element, actualTy)
        }
    }
}
