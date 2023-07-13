/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.MINUS
import org.rust.lang.core.psi.ext.RsOuterAttributeOwner
import org.rust.lang.core.psi.ext.elementType

class ReplaceIncDecOperatorFix private constructor(
    operator: PsiElement,
    private val replacement: String
) : RsQuickFixBase<PsiElement>(operator) {

    override fun getFamilyName() = text
    override fun getText() = RsBundle.message("intention.name.replace.inc.dec.operator", replacement)

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val parent = element.parent
        val subexpr = when (parent) {
            is RsPrefixIncExpr -> parent.expr
            is RsPostfixIncExpr -> parent.expr
            is RsPostfixDecExpr -> parent.expr
            is RsUnaryExpr -> (parent.expr as? RsUnaryExpr)?.expr
            else -> null
        } ?: return
        val newExpr = RsPsiFactory(project).createExpression("${subexpr.text} $replacement")
        parent.replace(newExpr)
    }

    companion object {
        fun create(operator: PsiElement): ReplaceIncDecOperatorFix? {
            if (operator !is RsInc && operator !is RsDec && operator.elementType != MINUS) return null

            val parent = operator.parent as? RsExpr
            val isApplicable = when (parent) {
                is RsPrefixIncExpr -> operator is RsInc && parent.expr != null
                is RsPostfixIncExpr -> operator is RsInc
                is RsPostfixDecExpr -> operator is RsDec
                is RsUnaryExpr -> parent.isNegation && parent.expr?.isNegation == true
                else -> false
            }
            if (!isApplicable) return null

            // TODO: support nested prefix/postfix expressions
            //       e.g. `a++ < n` -> `{ let tmp = a; a += 1; tmp } < n`
            //       or `++a < n` -> `{ a += 1; a } < n`
            val parentParent = parent?.parent as? RsExprStmt ?: return null

            // TODO: support prefix/postfix expressions with attributes
            //       e.g. `#[aaa] ++a` -> `#[aaa] { a += 1; a }`
            @Suppress("USELESS_IS_CHECK")
            val outerAttrList = when {
                parentParent is RsExprStmt -> parentParent.outerAttrList
                parent is RsPrefixIncExpr -> parent.outerAttrList
                parent is RsPostfixIncExpr -> (parent.expr as? RsOuterAttributeOwner)?.outerAttrList.orEmpty()
                parent is RsPostfixDecExpr -> (parent.expr as? RsOuterAttributeOwner)?.outerAttrList.orEmpty()
                parent is RsUnaryExpr -> parent.outerAttrList
                else -> emptyList()
            }
            if (outerAttrList.isNotEmpty()) return null

            val replacement = when {
                operator is RsInc -> "+= 1"
                operator is RsDec || parent.isNegation -> "-= 1"
                else -> return null
            }

            return ReplaceIncDecOperatorFix(operator, replacement)
        }
    }
}

private val PsiElement?.isNegation: Boolean
    get() = this is RsUnaryExpr && minus != null
