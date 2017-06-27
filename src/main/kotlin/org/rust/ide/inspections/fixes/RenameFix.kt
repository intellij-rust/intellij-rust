/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringFactory

/**
 * Fix that renames the given element.
 * @param element The element to be renamed.
 * @param newName The new name for the element.
 * @param fixName The name to use for the fix instead of the default one to better fit the inspection.
 */
class RenameFix(
    val element: PsiNamedElement,
    val newName: String,
    val fixName: String = "Rename to `$newName`"
) : LocalQuickFix {
    override fun getName() = fixName
    override fun getFamilyName() = "Rename element"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) =
        ApplicationManager.getApplication().invokeLater {
            RefactoringFactory.getInstance(project).createRename(element, newName).run()
        }
}
