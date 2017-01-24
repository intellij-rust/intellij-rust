package org.rust.ide.formatter

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsStructExprBody
import org.rust.lang.core.psi.util.elementType
import org.rust.lang.core.psi.util.getPrevNonCommentSibling

class RsCommaFormatProcessor : PostFormatProcessor {
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

        source.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement?) {
                super.visitElement(element)
                if (element is RsStructExprBody && helper.isElementFullyInRange(element)) {
                    if (fixStructExprBody(element)) {
                        helper.updateResultRange(1, 0)
                    }
                }
            }
        })

        return helper
    }

    companion object {
        /**
         * Delete trailing comma in one-line structs
         */
        fun fixStructExprBody(body: RsStructExprBody): Boolean {
            if (PostFormatProcessorHelper.isMultiline(body)) return false
            val lbrace = body.rbrace ?: return false
            val comma = lbrace.getPrevNonCommentSibling() ?: return false

            return if (comma.elementType == COMMA) {
                comma.delete()
                true
            } else {
                false
            }
        }
    }
}

