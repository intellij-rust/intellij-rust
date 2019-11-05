/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.psi.PsiElement
import org.rust.ide.colors.RsColor
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.ext.*

class RsEdition2018KeywordsAnnotator : AnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (!isEdition2018Keyword(element)) return

        val isEdition2018 = element.isEdition2018
        val isIdentifier = element.elementType == IDENTIFIER
        val isEnabledByCfg = element.isEnabledByCfg
        when {
            isEdition2018 && isIdentifier && isNameIdentifier(element) ->
                holder.createErrorAnnotation(element, "`${element.text}` is reserved keyword in Edition 2018")

            isEdition2018 && !isIdentifier && isEnabledByCfg ->
                holder.createInfoAnnotation(element, null).textAttributes = RsColor.KEYWORD.textAttributesKey

            isEdition2018 && !isIdentifier && !isEnabledByCfg -> {
                val colorScheme = EditorColorsManager.getInstance().globalScheme
                val keywordTextAttributes = colorScheme.getAttributes(RsColor.KEYWORD.textAttributesKey)
                val cfgDisabledCodeTextAttributes = colorScheme.getAttributes(RsColor.CFG_DISABLED_CODE.textAttributesKey)
                val cfgDisabledKeywordTextAttributes = TextAttributes.merge(keywordTextAttributes, cfgDisabledCodeTextAttributes)

                holder.createInfoAnnotation(element, null).enforcedTextAttributes = cfgDisabledKeywordTextAttributes
            }

            !isEdition2018 && !isIdentifier ->
                holder.createErrorAnnotation(element, "This feature is only available in Edition 2018")
        }
    }

    companion object {
        private val EDITION_2018_RESERVED_NAMES: Set<String> = hashSetOf("async", "await", "try")

        fun isEdition2018Keyword(element: PsiElement): Boolean =
            (element.elementType == IDENTIFIER && element.text in EDITION_2018_RESERVED_NAMES &&
                element.parent !is RsMacro && element.parent?.parent !is RsMacroCall &&
                element.parent !is RsFieldLookup ||
                element.elementType in RS_EDITION_2018_KEYWORDS) &&
                element.ancestorStrict<RsUseItem>() == null

        fun isNameIdentifier(element: PsiElement): Boolean {
            val parent = element.parent
            return parent is RsReferenceElement && element == parent.referenceNameElement ||
                parent is RsNameIdentifierOwner && element == parent.nameIdentifier
        }
    }
}
