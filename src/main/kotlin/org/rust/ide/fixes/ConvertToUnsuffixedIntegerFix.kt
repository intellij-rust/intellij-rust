/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.kind

class ConvertToUnsuffixedIntegerFix private constructor(
    element: RsLitExpr,
    @IntentionName private val textTemplate: String
): RsQuickFixBase<RsLitExpr>(element) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.convert.to.unsuffixed.integer")

    override fun getText(): String {
        return String.format(textTemplate, convertToUnsuffixedInteger(myStartElement.element))
    }


    override fun invoke(project: Project, editor: Editor?, element: RsLitExpr) {
        val integer = convertToUnsuffixedInteger(element) ?: return
        val psiFactory = RsPsiFactory(project)
        element.replace(psiFactory.createExpression(integer))
    }


    companion object {
        fun createIfCompatible(element: RsLitExpr, @IntentionName textTemplate: String): ConvertToUnsuffixedIntegerFix? {
            if (convertToUnsuffixedInteger(element) != null) {
                return ConvertToUnsuffixedIntegerFix(element, textTemplate)
            }
            return null
        }

        @IntentionName
        private fun convertToUnsuffixedInteger(element: PsiElement?): String? {
            if (element == null) return null
            if (element !is RsLitExpr) return null

            val value = when (val kind = element.kind) {
                is RsLiteralKind.Integer -> kind.value
                is RsLiteralKind.Boolean -> null
                is RsLiteralKind.Float -> kind.value?.toLong()
                is RsLiteralKind.String -> kind.value?.toLongOrNull()
                is RsLiteralKind.Char -> kind.value?.toLongOrNull()
                null -> null
            } ?: return null

            return value.toString()
        }
    }
}
