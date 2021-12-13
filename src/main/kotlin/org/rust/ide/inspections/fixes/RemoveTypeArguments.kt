/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.ide.inspections.getTypeArgumentsAndDeclaration
import org.rust.lang.core.psi.RsTypeArgumentList
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
        val (typeArguments) = getTypeArgumentsAndDeclaration(element) ?: return
        typeArguments?.removeTypeParameters()
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
