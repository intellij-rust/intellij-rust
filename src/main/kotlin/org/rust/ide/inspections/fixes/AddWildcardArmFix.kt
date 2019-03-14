/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.inspections.checkMatch.Pattern
import org.rust.lang.core.psi.RsMatchExpr
import org.rust.lang.core.psi.RsPsiFactory

class AddWildcardArmFix(match: RsMatchExpr) : LocalQuickFixOnPsiElement(match) {
    override fun getFamilyName() = "Add _ pattern"

    override fun getText() = familyName

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val oldMatchBody = (startElement as? RsMatchExpr)?.matchBody ?: return

        val newArm = RsPsiFactory(project).createMatchBody(listOf(Pattern.Wild)).matchArmList.first()
        oldMatchBody.addBefore(newArm, oldMatchBody.rbrace)
    }
}
