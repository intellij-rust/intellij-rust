/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import org.rust.openapiext.document

/**
 * Fix that removes a prefix of a certain path.
 *
 * E.g. `foo::bar::baz` -> `baz`.
 */
class RemovePathPrefixFix(
    file: PsiFile,
    range: TextRange,
) : LocalQuickFix {
    private val fileWithRange = SmartPointerManager.getInstance(file.project)
        .createSmartPsiFileRangePointer(file, range)

    override fun getName(): String = "Remove unnecessary path prefix"
    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = fileWithRange.containingFile ?: return
        val range = fileWithRange.range ?: return
        val document = file.document
        document?.deleteString(range.startOffset, range.endOffset)
    }
}
