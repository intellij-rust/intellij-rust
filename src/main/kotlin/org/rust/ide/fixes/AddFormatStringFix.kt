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
import org.rust.RsBundle
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory

class AddFormatStringFix(
    call: RsMacroCall,
    private val formatStringPosition: Int,
) : LocalQuickFixAndIntentionActionOnPsiElement(call) {

    override fun getText(): String = RsBundle.message("intention.name.add.format.string")
    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val call = startElement as? RsMacroCall ?: return
        val formatMacroArgument = call.formatMacroArgument ?: return
        val arguments = formatMacroArgument.formatMacroArgList
        val existingArgument = arguments.getOrNull(formatStringPosition)
        val anchor = existingArgument
            ?: formatMacroArgument.let { it.rbrace ?: it.rbrack ?: it.rparen }
            ?: return
        val factory = RsPsiFactory(project)
        val formatString = arguments
            .drop(formatStringPosition)
            .joinToString(" ", prefix = "\"", postfix = "\"") { "{}" }
        val formatStringArgument = factory.createFormatMacroArg(formatString)
        if (formatStringPosition != 0 && existingArgument == null) {
            formatMacroArgument.addBefore(factory.createComma(), anchor)
        }
        formatMacroArgument.addBefore(formatStringArgument, anchor)
        if (existingArgument != null) {
            formatMacroArgument.addBefore(factory.createComma(), anchor)
        }
    }
}
