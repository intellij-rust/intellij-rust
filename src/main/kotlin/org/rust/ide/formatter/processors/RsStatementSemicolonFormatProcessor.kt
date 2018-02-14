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
    override fun process(element: ASTNode, range: TextRange): TextRange {
        if (!shouldRunPunctuationProcessor(element)) return range

        val returns = arrayListOf<RsRetExpr>()
        val breaks = arrayListOf<RsBreakExpr>()
        val continues = arrayListOf<RsContExpr>()

        element.psi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.textRange in range) {
                    super.visitElement(element)
                }
                if (element is RsRetExpr && element.parent !is RsMatchArm) {
                    returns.add(element)
                }
                if (element is RsBreakExpr) {
                    breaks.add(element)
                }
                if (element is RsContExpr) {
                    continues.add(element)
                }
            }
        })

        val count = returns.count(::tryAddSemicolonAfter) +
            breaks.count(::tryAddSemicolonAfter) +
            continues.count(::tryAddSemicolonAfter)

        return range.grown(count)
    }

    private fun tryAddSemicolonAfter(element: RsExpr): Boolean {
        val nextSibling = element.getNextNonCommentSibling()
        if (nextSibling == null || nextSibling.elementType != SEMICOLON) {
            val psiFactory = RsPsiFactory(element.project)
            element.parent.addAfter(psiFactory.createSemicolon(), element)
            return true
        }
        return false
    }

}
