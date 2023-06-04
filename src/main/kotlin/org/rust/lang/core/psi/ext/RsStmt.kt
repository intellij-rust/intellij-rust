/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.stubs.RsExprStmtStub

abstract class RsStmtMixin : RsStubbedElementImpl<StubBase<*>>, RsStmt {
    constructor(node: ASTNode) : super(node)
    constructor(stub: StubBase<*>, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}

val RsExprStmt.hasSemicolon: Boolean get() = (greenStub as? RsExprStmtStub)?.hasSemicolon ?: (semicolon != null)
val RsExprStmt.isTailStmt: Boolean
    get() {
        if (hasSemicolon) return false
        val parent = parent
        return parent is RsBlock && parent.expandedTailExpr == expr
    }

fun RsExprStmt.addSemicolon() {
    if (hasSemicolon) return
    add(RsPsiFactory(project).createSemicolon())
}

fun RsExprStmt.addSemicolonIfNeeded() {
    if (needsSemicolon()) {
        addSemicolon()
    }
}

fun RsExprStmt.needsSemicolon(): Boolean =
    expr.needsSemicolon()

private fun RsExpr.needsSemicolon(): Boolean =
    this !is RsWhileExpr && this !is RsIfExpr && this !is RsForExpr && this !is RsLoopExpr && this !is RsMatchExpr
