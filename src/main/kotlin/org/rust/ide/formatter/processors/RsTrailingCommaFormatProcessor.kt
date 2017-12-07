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
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
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
                when {
                    commaList.removeTrailingComma(element) -> helper.updateResultRange(1, 0)
                    commaList.addTrailingCommaForElement(element) -> helper.updateResultRange(0, 1)
                }
            }
        })

        return helper
    }
}

/**
 * Delete trailing comma in one-line blocks
 */
fun CommaList.removeTrailingComma(list: PsiElement): Boolean {
    check(list.elementType == this.list)
    if (PostFormatProcessorHelper.isMultiline(list)) return false
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

fun CommaList.addTrailingCommaForElement(list: PsiElement): Boolean {
    check(list.elementType == this.list)
    if (!PostFormatProcessorHelper.isMultiline(list)) return false
    val rbrace = list.lastChild
    if (rbrace.elementType != closingBrace) return false

    val lastElement = rbrace.getPrevNonCommentSibling() ?: return false
    if (!isElement(lastElement)) return false

    val trailingSpace = list.node.chars.subSequence(
        lastElement.startOffsetInParent + lastElement.textLength,
        rbrace.startOffsetInParent
    )
    if (!trailingSpace.contains('\n')) return false

    if (list.firstChild.getNextNonCommentSibling() == lastElement) return false

    val comma = RsPsiFactory(list.project).createComma()
    list.addAfter(comma, lastElement)
    return true
}

fun CommaList.isLastElement(list: PsiElement, element: PsiElement): Boolean {
    check(list.elementType == this.list && isElement(element))
    val rbrace = list.lastChild
    if (rbrace.elementType != closingBrace) return false
    val lastElement = rbrace.getPrevNonCommentSibling() ?: return false
    return lastElement == element
}
