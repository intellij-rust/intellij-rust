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
import org.rust.lang.refactoring.RsPromoteModuleToDirectoryAction
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.getOrCreateModuleFile

/**
 * Creates module file by the given module declaration.
 */
class AddModuleFileFix(
    modDecl: RsModDeclItem,
    private val expandModuleFirst: Boolean
) : LocalQuickFixAndIntentionActionOnPsiElement(modDecl) {
    override fun getText(): String = "Create module file"

    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val modDecl = startElement as RsModDeclItem
        if (expandModuleFirst) {
            val containingFile = modDecl.containingFile as RsFile
            RsPromoteModuleToDirectoryAction.expandModule(containingFile)
        }
        modDecl.getOrCreateModuleFile()?.navigate(true)
    }

}
