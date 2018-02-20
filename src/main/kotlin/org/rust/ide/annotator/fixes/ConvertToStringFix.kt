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
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory

/**
 * For the given `expr` adds `to_string()` call. Note the fix doesn't attempt to check if adding the function call
 * will produce a valid expression.
 */
class ConvertToStringFix(expr: PsiElement) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {

    override fun getFamilyName(): String = "Convert to type"

    override fun getText(): String = "Convert to String using `ToString` trait"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsExpr) return
        startElement.replace(RsPsiFactory(project).createNoArgsMethodCall(startElement, "to_string"))
    }
}
