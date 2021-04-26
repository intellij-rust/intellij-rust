/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsValueArgumentList
import org.rust.lang.core.psi.ext.deleteWithSurroundingCommaAndWhitespace

class RemoveRedundantFunctionArgumentsFix(
    element: RsValueArgumentList,
    private val expectedCount: Int
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getText(): String = "Remove redundant arguments"
    override fun getFamilyName(): String = text
    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val args = startElement as? RsValueArgumentList ?: return
        val extraArgs = args.exprList.drop(expectedCount)
        for (arg in extraArgs) {
            arg.deleteWithSurroundingCommaAndWhitespace()
        }
    }
}
