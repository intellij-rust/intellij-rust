/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import com.intellij.psi.PsiElement
import org.rust.ide.actions.mover.RsLineMover.Companion.RangeEndpoint
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.ancestors
import org.rust.lang.core.psi.ext.elementType

class RsStatementUpDownMover : RsLineMover() {
    private val movableItems = tokenSetOf(
        RsElementTypes.STMT,
        RsElementTypes.EXPR_STMT,
        RsElementTypes.EMPTY_STMT,
        RsElementTypes.LET_DECL,
        RsElementTypes.EXPR_STMT_OR_LAST_EXPR
    )

    private val PsiElement.isBlockExpr: Boolean
        get() = this is RsExpr && parent is RsBlock

    override fun findMovableAncestor(psi: PsiElement, endpoint: RangeEndpoint): PsiElement? =
        psi.ancestors.find { it.elementType in movableItems || it.isBlockExpr }

    override fun fixupSibling(sibling: PsiElement, down: Boolean): PsiElement? {
        val block = getClosestBlock(sibling, down) ?: return sibling
        return if (down) block.lbrace else block.rbrace
    }

    override fun findTargetElement(sibling: PsiElement, down: Boolean): PsiElement? {
        if (isMovingOutOfFunctionBody(sibling, down)) {
            UpDownMoverTestMarks.moveOutOfBody.hit()
            return null
        }
        return sibling
    }

    private fun getClosestBlock(element: PsiElement, down: Boolean): RsBlock? =
        when (element) {
            is RsWhileExpr -> element.block
            is RsLoopExpr -> element.block
            is RsForExpr -> element.block
            is RsBlockExpr -> element.block
            is RsIfExpr -> if (down) element.block else element.elseBranch?.block ?: element.block
            is RsLambdaExpr -> element.expr?.let { getClosestBlock(it, down) }
            is RsExprStmt -> getClosestBlock(element.expr, down)
            else -> null
        }
}
