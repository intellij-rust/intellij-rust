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

open class RemoveElementFix(
    element: PsiElement,
    private val removingElementName: String = "`${element.text}`"
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "Remove"
    override fun getText(): String = "Remove $removingElementName"
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        startElement.delete()
    }
}
