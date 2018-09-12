/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsPsiFactory

class AddCrateKeywordFix(path: RsPath) : LocalQuickFixAndIntentionActionOnPsiElement(path) {

    override fun getFamilyName(): String = text
    override fun getText(): String = "Add `crate` at the beginning of path"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsPath) return
        val originalPathText = startElement.text.removePrefix("::")
        val newPath = RsPsiFactory(project).tryCreatePath("crate::$originalPathText") ?: return
        startElement.replace(newPath)
    }
}
