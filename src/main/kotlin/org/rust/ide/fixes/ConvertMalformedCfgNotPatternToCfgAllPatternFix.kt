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
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsMetaItemArgs
import org.rust.lang.core.psi.RsPsiFactory

class ConvertMalformedCfgNotPatternToCfgAllPatternFix(element: RsMetaItem) : LocalQuickFixAndIntentionActionOnPsiElement(element) {
    private val fixText = run {
        val metaItemList = element.metaItemArgs ?: return@run ""
        val negatedArguments = convertToAllPatternWithNegatedArguments(metaItemList)

        "Convert to `$negatedArguments`"
    }

    override fun getFamilyName(): String = "Convert `not(a, b)` cfg-pattern to `all(not(a), not(b))`"
    override fun getText(): String = fixText

    override fun invoke(project: Project, file: PsiFile, editor: Editor?, startElement: PsiElement, endElement: PsiElement) {
        if (startElement !is RsMetaItem) return
        val metaItemList = startElement.metaItemArgs ?: return

        val factory = RsPsiFactory(project)
        val newItem = factory.createMetaItem(convertToAllPatternWithNegatedArguments(metaItemList))
        val replaced = startElement.replace(newItem) as? RsMetaItem ?: return

        val offset = replaced.metaItemArgs?.lparen?.textOffset ?: return
        editor?.caretModel?.moveToOffset(offset + 1)
    }

    private fun convertToAllPatternWithNegatedArguments(metaItemList: RsMetaItemArgs) =
        metaItemList.metaItemList.joinToString(prefix = "all(", separator = ", ", postfix = ")") {
            "not(${it.text})"
        }

    companion object {
        fun createIfCompatible(element: PsiElement): ConvertMalformedCfgNotPatternToCfgAllPatternFix? {
            val metaItem = element as? RsMetaItem ?: return null
            if (metaItem.metaItemArgsList.size > 1) {
                return ConvertMalformedCfgNotPatternToCfgAllPatternFix(element)
            }
            return null
        }
    }
}
