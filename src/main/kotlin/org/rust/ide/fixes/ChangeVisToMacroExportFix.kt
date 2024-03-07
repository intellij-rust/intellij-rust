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
import org.rust.lang.core.psi.RsVis

class ChangeVisToMacroExportFix(
    vis: RsVis
) : LocalQuickFixAndIntentionActionOnPsiElement(vis) {

    private val visDisplay = vis.text
    override fun getFamilyName() = "Replace visibility modifier with `#[macro_export]`"
    override fun getText() = "Replace `$visDisplay` with `#[macro_export]`"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        val macroExport = RsPsiFactory(project).createOuterAttr("macro_export")
        startElement.parent.addBefore(macroExport, startElement)
        startElement.delete()
    }
}
