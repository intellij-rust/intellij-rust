/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.psi.RsMetaItemArgs
import org.rust.lang.core.psi.RsPsiFactory

class ConvertMalformedCfgNotPatternToCfgAllPatternFix(element: RsMetaItem) : RsQuickFixBase<RsMetaItem>(element) {
    @IntentionName
    private val fixText = run {
        val metaItemList = element.metaItemArgs ?: return@run ""
        val negatedArguments = convertToAllPatternWithNegatedArguments(metaItemList)

        RsBundle.message("intention.name.convert.to", negatedArguments)
    }

    override fun getFamilyName(): String = RsBundle.message("intention.family.name.convert.not.b.cfg.pattern.to.all.not.not.b")
    override fun getText(): String = fixText

    override fun invoke(project: Project, editor: Editor?, element: RsMetaItem) {
        val metaItemList = element.metaItemArgs ?: return

        val factory = RsPsiFactory(project)
        val newItem = factory.createMetaItem(convertToAllPatternWithNegatedArguments(metaItemList))
        val replaced = element.replace(newItem) as? RsMetaItem ?: return

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
