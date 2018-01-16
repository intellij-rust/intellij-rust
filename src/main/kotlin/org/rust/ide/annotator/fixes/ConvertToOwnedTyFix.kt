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
 * For the given `expr` converts it to the owned type with with `(expr).to_owned()`
 */
class ConvertToOwnedTyFix(expr: PsiElement): LocalQuickFixAndIntentionActionOnPsiElement(expr) {
    override fun getFamilyName(): String = "Convert to type"

    override fun getText(): String = "Convert to owned type using `ToOwned` trait"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsExpr) return
        startElement.replace(RsPsiFactory(project).createNoArgsMethodCall(startElement, "to_owned"))
    }

}
