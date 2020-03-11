/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringFactory

/**
 * Fix that renames the given element.
 * @param element The element to be renamed.
 * @param newName The new name for the element.
 * @param fixName The name to use for the fix instead of the default one to better fit the inspection.
 */
class RenameFix(
    element: PsiNamedElement,
    val newName: String,
    val fixName: String = "Rename to `$newName`"
) : LocalQuickFixOnPsiElement(element) {
    override fun getText() = fixName
    override fun getFamilyName() = "Rename element"

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) =
        invokeLater {
            RefactoringFactory.getInstance(project).createRename(startElement, newName).run()
        }
}
