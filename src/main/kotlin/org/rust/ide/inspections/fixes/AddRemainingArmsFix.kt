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

class AddRemainingArmsFix(match: RsMatchExpr, val patterns: List<Pattern>) : LocalQuickFixOnPsiElement(match) {
    override fun getFamilyName() = "Add remaining patterns"

    override fun getText() = familyName

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val oldMatchBody = (startElement as? RsMatchExpr)?.matchBody ?: return

        val newArms = RsPsiFactory(project).createMatchBody(patterns, oldMatchBody).matchArmList
        for (arm in newArms) {
            oldMatchBody.addBefore(arm, oldMatchBody.rbrace)
        }
    }
}
