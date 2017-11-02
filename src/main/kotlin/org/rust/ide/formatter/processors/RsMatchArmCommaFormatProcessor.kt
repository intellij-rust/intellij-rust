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
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsElementTypes.COMMA
import org.rust.lang.core.psi.RsMatchArm
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getNextNonCommentSibling

class RsMatchArmCommaFormatProcessor : PreFormatProcessor {
    override fun process(element: ASTNode, range: TextRange): TextRange {
        if (!shouldRunPunctuationProcessor(element)) return range

        var nRemovedCommas = 0
        element.psi.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (range.contains(element.textRange)) {
                    super.visitElement(element)
                }

                if (element is RsMatchArm && removeCommaAfterBlock(element)) {
                    nRemovedCommas += 1
                }
            }
        })
        return range.grown(-nRemovedCommas)
    }

    private fun removeCommaAfterBlock(element: RsMatchArm): Boolean {
        val expr = element.expr ?: return false
        if (!(expr is RsBlockExpr && expr.unsafe == null)) return false
        val comma = expr.getNextNonCommentSibling() ?: return false
        if (comma.elementType == COMMA) {
            comma.delete()
            return true
        }
        return false
    }
}
