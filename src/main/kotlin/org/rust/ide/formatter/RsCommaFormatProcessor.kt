package org.rust.ide.formatter

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessorHelper
import org.rust.lang.core.psi.RsBlockFields
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsElementTypes.RBRACE
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
                if (!helper.isElementFullyInRange(element)) return
                when (element) {
                    is RsStructExprBody, is RsBlockFields -> {
                        if (fixSingleLineBracedBlock(element)) {
                            helper.updateResultRange(1, 0)
                        }
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
        fun fixSingleLineBracedBlock(block: PsiElement): Boolean {
            if (PostFormatProcessorHelper.isMultiline(block)) return false
            val rbrace = block.lastChild
            if (rbrace.elementType != RBRACE) return false
            val comma = rbrace.getPrevNonCommentSibling() ?: return false

            return if (comma.elementType == COMMA) {
                comma.delete()
                true
            } else {
                false
            }
        }
    }
}

