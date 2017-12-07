/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.processors

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper
import org.rust.ide.formatter.impl.CommaList
import org.rust.ide.formatter.rust
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling

class RsTrailingCommaFormatProcessor : PostFormatProcessor {
    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
        doProcess(source, settings, null)
        return source
    }

    override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
        return doProcess(source, settings, rangeToReformat).resultTextRange
    }

    private fun doProcess(source: PsiElement, settings: CodeStyleSettings, range: TextRange? = null): PostFormatProcessorHelper {
        val helper = PostFormatProcessorHelper(settings)
        helper.resultTextRange = range
        if (settings.rust.PRESERVE_PUNCTUATION) return helper

        source.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (!helper.isElementFullyInRange(element)) return
                val commaList = CommaList.forElement(element.elementType) ?: return
                if (fixSingleLineBracedBlock(element, commaList)) {
                    helper.updateResultRange(1, 0)
                }
            }
        })

        return helper
    }

    companion object {
        /**
         * Delete trailing comma in one-line blocks
         */
        fun fixSingleLineBracedBlock(block: PsiElement, commaList: CommaList): Boolean {
            if (PostFormatProcessorHelper.isMultiline(block)) return false
            return commaList.removeTrailingComma(block)
        }
    }
}

fun CommaList.removeTrailingComma(list: PsiElement): Boolean {
    check(list.elementType == this.list)
    val rbrace = list.lastChild
    if (rbrace.elementType != closingBrace) return false
    val comma = rbrace.getPrevNonCommentSibling() ?: return false
    return if (comma.elementType == COMMA) {
        comma.delete()
        true
    } else {
        false
    }
}

fun CommaList.addTrailingCommaForElement(list: PsiElement, element: PsiElement): Boolean {
    check(list.elementType == this.list && isElement(element))
    val rbrace = list.lastChild
    if (rbrace.elementType != closingBrace) return false
    val lastChild = rbrace.getPrevNonCommentSibling() ?: return false
    if (lastChild != element) return false
    val comma = RsPsiFactory(list.project).createComma()
    list.addAfter(comma,element)
    return true
}
