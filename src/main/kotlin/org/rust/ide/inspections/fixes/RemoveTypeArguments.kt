/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.deleteWithSurroundingComma
import org.rust.lang.core.psi.ext.getNextNonCommentSibling

class RemoveTypeArguments(
    private val startIndex: Int,
    private val endIndex: Int
) : LocalQuickFix {

    override fun getFamilyName() = "Remove redundant type arguments"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? RsElement ?: return
        when (element) {
            is RsBaseType ->
                element.path?.typeArgumentList
            is RsMethodCall ->
                element.typeArgumentList
            is RsCallExpr ->
                (element.expr as? RsPathExpr)?.path?.typeArgumentList
            else -> null
        }?.removeTypeParameters() ?: return
    }

    private fun RsTypeArgumentList.removeTypeParameters() {
        val count = endIndex - startIndex
        for (i in 0 until count) {
            typeReferenceList[startIndex].deleteWithSurroundingComma()
        }
        // If the type argument list is empty, delete it
        if (lt.getNextNonCommentSibling() == gt) {
            delete()
        }
    }
}
