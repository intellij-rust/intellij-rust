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
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsVisRestriction

class FixVisRestriction(visRestriction: RsVisRestriction) : LocalQuickFixAndIntentionActionOnPsiElement(visRestriction) {

    override fun getText(): String = "Fix visibility restriction"
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsVisRestriction) return
        startElement.addBefore(RsPsiFactory(project).createIn(), startElement.path)
    }
}
