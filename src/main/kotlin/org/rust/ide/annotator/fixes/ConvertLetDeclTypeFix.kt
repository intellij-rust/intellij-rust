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
import org.rust.ide.presentation.renderInsertionSafe
import org.rust.lang.core.psi.RsLetDecl
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.types.ty.Ty

/**
 * Change the declared type of a local variable.
 */
class ConvertLetDeclTypeFix(
    decl: RsLetDecl,
    private val fixText: String,
    private val ty: Ty
) : LocalQuickFixAndIntentionActionOnPsiElement(decl) {
    override fun getFamilyName(): String = "Convert type of local variable"
    override fun getText(): String = fixText

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val decl = startElement as? RsLetDecl ?: return
        val factory = RsPsiFactory(project)
        val type = factory.tryCreateType(ty.renderInsertionSafe(useAliasNames = true)) ?: return

        decl.typeReference?.replace(type)
    }
}
