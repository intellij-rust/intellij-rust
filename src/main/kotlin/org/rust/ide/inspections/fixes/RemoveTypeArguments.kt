/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement

class RemoveTypeArguments(
    element: RsElement,
    private val startIndex: Int,
    private val endIndex: Int
) : LocalQuickFixOnPsiElement(element) {

    override fun getFamilyName(): String = text
    override fun getText(): String = "Remove redundant type arguments"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        when (startElement) {
            is RsBaseType ->
                startElement.path?.typeArgumentList
            is RsMethodCall ->
                startElement.typeArgumentList
            is RsCallExpr ->
                (startElement.expr as? RsPathExpr)?.path?.typeArgumentList
            else -> null
        }?.removeTypeParameters() ?: return
    }

    private fun RsTypeArgumentList.removeTypeParameters() {
        if (lifetimeList.size > 0) return

        if (startIndex == 0) {
            delete()
        } else {
            val startElement = typeReferenceList[startIndex - 1].nextSibling
            val endElement = typeReferenceList[endIndex - 1]

            deleteChildRange(startElement, endElement)
        }
    }
}
