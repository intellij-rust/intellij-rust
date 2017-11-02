/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter.processors

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsUseGlob
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling
import org.rust.lang.core.psi.ext.isSelf

/**
 * Pre format processor ensuring that if an import statement only contains a single import from a crate that
 * there are no curly braces surrounding it.
 *
 * For example the following would change like so:
 *
 * `use getopts::{optopt};` --> `use getopts::optopt;`
 *
 * While this wouldn't change at all:
 *
 * `use getopts::{optopt, optarg};`
 *
 */
class RsSingleImportRemoveBracesFormatProcessor : PreFormatProcessor {
    override fun process(element: ASTNode, range: TextRange): TextRange {
        if (!shouldRunPunctuationProcessor(element)) return range

        var numRemovedBraces = 0
        element.psi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (range.contains(element.textRange)) {
                    super.visitElement(element)
                }

                if (element is RsUseGlob && removeBracesAroundSingleImport(element)) {
                    numRemovedBraces += 2
                }
            }
        })
        return range.grown(-numRemovedBraces)
    }

    fun removeBracesAroundSingleImport(element: RsUseGlob): Boolean {
        if (element.elementType != RsElementTypes.USE_GLOB) return false
        if (element.children.size > 1) return false

        val leftBrace = element.getPrevNonCommentSibling() ?: return false
        val rightBrace = element.getNextNonCommentSibling() ?: return false
        if (leftBrace.elementType == RsElementTypes.LBRACE &&
            rightBrace.elementType == RsElementTypes.RBRACE &&
            !element.isSelf) {
            leftBrace.delete()
            rightBrace.delete()
            return true
        }

        return false
    }
}
