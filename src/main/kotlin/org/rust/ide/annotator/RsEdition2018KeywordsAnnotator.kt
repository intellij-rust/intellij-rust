/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.IDENTIFIER
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.isEdition2018

class RsEdition2018KeywordsAnnotator : RsAnnotatorBase() {
    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        if (!isEdition2018Keyword(element)) return

        val isEdition2018 = element.isEdition2018
        val isIdentifier = element.elementType == IDENTIFIER
        when {
            isEdition2018 && isIdentifier ->
                holder.createErrorAnnotation(element, "`${element.text}` is reserved keyword in Edition 2018")
            isEdition2018 && !isIdentifier ->
                holder.createInfoAnnotation(element, null).textAttributes = RsColor.KEYWORD.textAttributesKey
            !isEdition2018 && !isIdentifier ->
                holder.createErrorAnnotation(element, "This feature is only available in Edition 2018")
        }
    }

    companion object {
        private val EDITION_2018_RESERVED_NAMES: Set<String> = hashSetOf("async", "await", "try")

        private val IGNORED_ELEMENTS: Array<Class<out RsElement>> =
            arrayOf(RsMacroBody::class.java, RsMacroArgument::class.java, RsUseItem::class.java)

        fun isEdition2018Keyword(element: PsiElement): Boolean =
            (element.elementType == IDENTIFIER && element.text in EDITION_2018_RESERVED_NAMES &&
                element.parent !is RsMacro && element.parent?.parent !is RsMacroCall ||
                element.elementType in RS_EDITION_2018_KEYWORDS) &&
                PsiTreeUtil.getParentOfType(element, *IGNORED_ELEMENTS) == null
    }
}
