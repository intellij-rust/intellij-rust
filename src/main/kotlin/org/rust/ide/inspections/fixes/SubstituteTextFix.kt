/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

/**
 * Fix that removes the given range from the document and places a text onto its place.
 * @param range The range that will be removed from the document.
 * @param text The text that will be placed starting from `range.startOffset`. If `null`, no text will be inserted.
 * @param fixName The name to use for the fix instead of the default one to better fit the inspection.
 */
class SubstituteTextFix(
    val range: TextRange,
    val text: String?,
    val fixName: String = "Substitute"
) : LocalQuickFix {
    override fun getName() = fixName
    override fun getFamilyName() = "Substitute one text to another"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
        document?.deleteString(range.startOffset, range.endOffset)
        if (text != null) {
            document?.insertString(range.startOffset, text)
        }
    }
}
