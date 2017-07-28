/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.codeStyle.PreFormatProcessor
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsRetExpr
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import java.util.*

class RsReturnStatementFormatProcessor : PreFormatProcessor {
    override fun process(element: ASTNode, range: TextRange): TextRange {
        if (CodeStyleSettingsManager.getInstance(element.psi.project).currentSettings.rust.PRESERVE_PUNCTUATION) return range

        val returnElements = ArrayList<RsRetExpr>()
        element.psi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.textRange in range) {
                    super.visitElement(element)
                }

                if (element is RsRetExpr && element.parent !is RsMatchArm) {
                    returnElements.add(element)
                }
            }
        })

        var nAddedSemicolons = 0
        for (returnElement in returnElements) {
            if (tryAddSemicolonAfterReturnExpression(returnElement)) {
                nAddedSemicolons++
            }
        }

        return range.grown(nAddedSemicolons)
    }

    private fun tryAddSemicolonAfterReturnExpression(element: RsRetExpr): Boolean {
        val nextSibling = element.getNextNonCommentSibling();
        if (nextSibling == null || nextSibling.elementType != SEMICOLON) {
            val psiFactory = RsPsiFactory(element.project)
            element.parent.addAfter(psiFactory.createSemicolon(), element)
            return true
        }
        return false
    }

}
