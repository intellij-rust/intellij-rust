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
import org.rust.lang.core.types.ty.Ty

/**
 * For the given `expr` adds cast to the given type `ty`
 */
class AddAsTyFix(expr: PsiElement, val ty: Ty) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {

    override fun getFamilyName(): String = "Add safe cast"

    override fun getText(): String = "Add safe cast to $ty"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsExpr) return
        startElement.replace(RsPsiFactory(project).createCastExpr(startElement, ty.toString()))
    }
}
