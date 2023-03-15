/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.ide.inspections.getTypeArgumentsAndDeclaration
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.ext.RsMethodOrPath
import org.rust.lang.core.psi.ext.deleteWithSurroundingComma
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.startOffset

class RemoveGenericArguments(
    private val startIndex: Int,
    private val endIndex: Int
) : LocalQuickFix {
    override fun getFamilyName() = "Remove redundant generic arguments"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? RsMethodOrPath ?: return
        val (typeArguments) = getTypeArgumentsAndDeclaration(element) ?: return
        typeArguments?.removeTypeParameters()
    }

    private fun RsTypeArgumentList.removeTypeParameters() {
        (typeReferenceList + exprList)
            .sortedBy { it.startOffset }
            .subList(startIndex, endIndex)
            .forEach { it.deleteWithSurroundingComma() }
        // If the type argument list is empty, delete it
        if (lt.getNextNonCommentSibling() == gt) {
            delete()
        }
    }
}
