/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RS_BINARY_OPS
import org.rust.lang.core.psi.RsBinaryOp
import org.rust.lang.core.resolve.ref.RsBinaryOpReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsBinaryOpStub

val RsBinaryOp.operator: PsiElement
    get() = requireNotNull(node.findChildByType(RS_BINARY_OPS)) { "guaranteed to be not-null by parser" }.psi

val RsBinaryOp.op: String get() = stub?.op ?: operator.text

val RsBinaryOp.isLazy: Boolean get() = andand != null || this.oror != null

abstract class RsBinaryOpImplMixin : RsStubbedElementImpl<RsBinaryOpStub>, RsBinaryOp {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsBinaryOpStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val referenceNameElement: PsiElement get() = operator

    override fun getReference(): RsReference = RsBinaryOpReferenceImpl(this)
}
