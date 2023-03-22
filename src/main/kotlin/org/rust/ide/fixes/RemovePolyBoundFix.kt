/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsPolybound
import org.rust.lang.core.psi.ext.deleteWithSurroundingPlus

class RemovePolyBoundFix(
    bound: RsPolybound,
    private val boundName: String = "`${bound.text}`"
) : LocalQuickFixOnPsiElement(bound) {
    override fun getText() = "Remove $boundName bound"
    override fun getFamilyName() = "Remove bound"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val bound = (startElement as? RsPolybound) ?: return
        bound.deleteWithSurroundingPlus()
    }
}
