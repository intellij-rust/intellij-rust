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
import org.rust.ide.presentation.tyToStringWithoutTypeArgs
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty

/**
 * For the given `expr` converts it to the type `ty` with `ty::from(expr)`
 */
class ConvertToTyUsingFromTraitFix(expr: PsiElement, val ty: Ty) : LocalQuickFixAndIntentionActionOnPsiElement(expr) {
    override fun getFamilyName(): String = "Convert to type"

    override fun getText(): String = "Convert to type $ty using `From` trait"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsExpr) return
        startElement.replace(RsPsiFactory(project).createAssocFunctionCall(tyToStringWithoutTypeArgs(ty), "from", listOf(startElement)))
    }
}
