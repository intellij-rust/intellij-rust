/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsPsiFactory

class EscapeKeywordFix(
    element: PsiElement,
    private val isKeyword: Boolean
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {

    override fun getFamilyName(): String = "Escape keyword"
    override fun getText(): String = if (isKeyword) "Escape keyword" else "Escape reserved keyword"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val name = startElement.text
        startElement.replace(RsPsiFactory(project).createIdentifier("r#${name}"))
    }
}
