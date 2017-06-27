/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import org.rust.lang.core.psi.RsExpr


/**
 * Fix that converts the given reference to owned value.
 * @param argEl An element, that represents a reference from which the first
 * symbol '&' must be removed.
 * @param fixName A name to use for the fix instead of the default one to better fit the inspection.
 */
class RemoveRefFix(
    val argEl: RsExpr,
    val fixName: String = "Change reference to owned value"
) : LocalQuickFix {
    override fun getName() = fixName
    override fun getFamilyName() = "Change reference to owned value"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        if (argEl.text != null && argEl.text[0] == '&') {
            val offset = argEl.textRange.startOffset
            val document = PsiDocumentManager.getInstance(project).getDocument(argEl.containingFile)
            document?.deleteString(offset, offset + 1)
        }
    }

}
