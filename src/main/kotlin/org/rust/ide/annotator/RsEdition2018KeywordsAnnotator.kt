/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.RsBundle
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.isUnitTestMode

class RsEdition2018KeywordsAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (element.edition == null) return

        if (!isEdition2018Keyword(element)) return

        val isAtLeastEdition2018 = element.isAtLeastEdition2018
        val isIdentifier = element.elementType == IDENTIFIER
        val isEnabledByCfg = element.isEnabledByCfg
        when {
            isAtLeastEdition2018 && isIdentifier && isNameIdentifier(element) ->
                holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.reserved.keyword.in.edition", element.text)).create()

            isAtLeastEdition2018 && !isIdentifier && isEnabledByCfg -> {
                if (!holder.isBatchMode) {
                    val severity = if (isUnitTestMode) RsColor.KEYWORD.testSeverity else HighlightSeverity.INFORMATION
                    holder.newSilentAnnotation(severity)
                        .textAttributes(RsColor.KEYWORD.textAttributesKey).create()
                }
            }

            isAtLeastEdition2018 && !isIdentifier && !isEnabledByCfg -> {
                if (!holder.isBatchMode) {
                    val colorScheme = EditorColorsManager.getInstance().globalScheme
                    val keywordTextAttributes = colorScheme.getAttributes(RsColor.KEYWORD.textAttributesKey)
                    val cfgDisabledCodeTextAttributes = colorScheme.getAttributes(RsColor.CFG_DISABLED_CODE.textAttributesKey)
                    val cfgDisabledKeywordTextAttributes = TextAttributes.merge(keywordTextAttributes, cfgDisabledCodeTextAttributes)

                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .enforcedTextAttributes(cfgDisabledKeywordTextAttributes).create()
                }
            }

            !isAtLeastEdition2018 && !isIdentifier ->
                holder.newAnnotation(HighlightSeverity.ERROR, RsBundle.message("inspection.message.this.feature.only.available.in.edition")).create()
        }
    }

    companion object {
        private val EDITION_2018_RESERVED_NAMES: Set<String> = hashSetOf("async", "await", "try")

        fun isEdition2018Keyword(element: PsiElement): Boolean =
            (element.elementType == IDENTIFIER && element.text in EDITION_2018_RESERVED_NAMES &&
                element.parent !is RsMacro && element.parent?.parent !is RsMacroCall &&
                element.parent !is RsFieldLookup ||
                element.elementType in RS_EDITION_2018_KEYWORDS) &&
                PsiTreeUtil.getParentOfType(element, RsUseItem::class.java, RsMetaItemArgs::class.java) == null

        fun isNameIdentifier(element: PsiElement): Boolean {
            val parent = element.parent
            return parent is RsReferenceElement && element == parent.referenceNameElement ||
                parent is RsNameIdentifierOwner && element == parent.nameIdentifier
        }
    }
}
