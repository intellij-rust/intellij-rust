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
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.SEMICOLON
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling

class RsStatementSemicolonFormatProcessor : PreFormatProcessor {
    override fun process(node: ASTNode, range: TextRange): TextRange {
        if (!shouldRunPunctuationProcessor(node)) return range

        val elements = arrayListOf<PsiElement>()

        node.psi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.textRange in range) {
                    super.visitElement(element)
                }

                // no semicolons inside "match"
                if (element.parent !is RsMatchArm) {
                    if (element is RsRetExpr || element is RsBreakExpr || element is RsContExpr) {
                        elements.add(element)
                    }
                }
            }
        })

        return range.grown(elements.count(::tryAddSemicolonAfter))
    }

    private fun tryAddSemicolonAfter(element: PsiElement): Boolean {
        val nextSibling = element.getNextNonCommentSibling()
        if (nextSibling == null || nextSibling.elementType != SEMICOLON) {
            val psiFactory = RsPsiFactory(element.project)
            element.parent.addAfter(psiFactory.createSemicolon(), element)
            return true
        }
        return false
    }

}
