/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.elementType
import org.rust.lang.core.psi.RsAssocTypeBinding
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.ext.getNextNonCommentSibling

class RemoveAssocTypeBindingFix(binding: PsiElement) : LocalQuickFixOnPsiElement(binding) {
    override fun getFamilyName(): String = text
    override fun getText(): String = "Remove redundant associated type"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val binding = startElement as? RsAssocTypeBinding ?: return
        val parent = binding.parent as? RsTypeArgumentList

        val next = binding.getNextNonCommentSibling()
        binding.delete()

        if (next?.elementType == COMMA) {
            next?.delete()
        }

        if (parent != null &&
            parent.typeReferenceList.isEmpty() &&
            parent.assocTypeBindingList.isEmpty() &&
            parent.lifetimeList.isEmpty()) {
            parent.delete()
        }
    }
}
