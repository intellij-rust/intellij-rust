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
import org.rust.lang.core.psi.RsCastExpr

open class RemoveCastFix(
    element: RsCastExpr
) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    private val fixText: String = "Remove `as ${element.typeReference.text}`"
    override fun getFamilyName(): String = "Remove unnecessary cast"
    override fun getText(): String = fixText
    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsCastExpr) return

        startElement.replace(startElement.expr)
    }
}
