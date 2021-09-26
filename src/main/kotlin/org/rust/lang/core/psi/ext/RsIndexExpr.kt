/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsIndexExpr
import org.rust.lang.core.resolve.ref.RsIndexExprReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsPlaceholderStub

val RsIndexExpr.containerExpr: RsExpr?
    get() = exprList.getOrNull(0)

val RsIndexExpr.indexExpr: RsExpr?
    get() = exprList.getOrNull(1)

abstract class RsIndexExprImplMixin : RsStubbedElementImpl<RsPlaceholderStub>, RsIndexExpr {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPlaceholderStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val referenceNameElement: PsiElement? = null

    override fun getReference(): RsReference = RsIndexExprReferenceImpl(this)
}
