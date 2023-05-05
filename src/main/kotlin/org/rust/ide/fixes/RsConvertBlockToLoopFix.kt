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
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsWhileExpr
import org.rust.lang.core.psi.ext.RsLabelReferenceOwner
import org.rust.lang.core.psi.ext.ancestorStrict

class RsConvertBlockToLoopFix(element: RsBlockExpr): LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "Convert to loop"

    override fun getText(): String = familyName

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, blockExpr: PsiElement, endElement: PsiElement) {
        val rsBlockExpr = blockExpr as RsBlockExpr
        val labelName = rsBlockExpr.labelDecl?.name ?: return
        rsBlockExpr.replace(RsPsiFactory(project).createLoop(rsBlockExpr.block.text, labelName))
    }
}
