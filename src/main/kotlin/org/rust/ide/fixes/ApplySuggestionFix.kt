/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.cargo.toolchain.impl.Applicability
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset

class ApplySuggestionFix(
    private val message: String,
    private val replacement: String,
    val applicability: Applicability,
    startElement: PsiElement,
    endElement: PsiElement,
    val textRange: TextRange
) : LocalQuickFixAndIntentionActionOnPsiElement(startElement, endElement) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.apply.suggested.replacement.made.by.external.linter")
    override fun getText(): String = RsBundle.message("intention.name.external.linter", message)

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val document = editor?.document ?: file.viewProvider.document ?: return
        document.replaceString(startElement.startOffset, endElement.endOffset, replacement)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApplySuggestionFix

        if (message != other.message) return false
        if (replacement != other.replacement) return false
        if (myStartElement != other.myStartElement) return false
        if (myEndElement != other.myEndElement) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message.hashCode()
        result = 31 * result + replacement.hashCode()
        result = 31 * result + (myStartElement?.hashCode() ?: 0)
        result = 31 * result + (myEndElement?.hashCode() ?: 0)
        return result
    }
}
