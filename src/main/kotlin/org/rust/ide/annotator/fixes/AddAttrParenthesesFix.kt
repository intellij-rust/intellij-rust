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
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsPsiFactory

class AddAttrParenthesesFix(element: RsMetaItem, private val attrName: String) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    override fun getFamilyName(): String = "Add parentheses"
    override fun getText(): String = "Add parentheses to `$attrName`"

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsMetaItem) return

        val newItem = RsPsiFactory(project).createOuterAttr("$attrName()").metaItem
        val replaced = startElement.replace(newItem) as? RsMetaItem ?: return

        // Place caret between parentheses, so the user can immediately start typing
        val offset = replaced.metaItemArgs?.lparen?.textOffset ?: return
        editor?.caretModel?.moveToOffset(offset + 1)
    }
}
