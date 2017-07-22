/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.core.resolve.ref.RsMetaItemReferenceImpl
import org.rust.lang.core.resolve.ref.RsReference
import org.rust.lang.core.stubs.RsMetaItemStub

val RsMetaItem.value: String? get() {
    val stub = stub
    return if (stub != null) stub.value else litExpr?.stringLiteralValue
}

val RsMetaItem.hasEq: Boolean get() = stub?.hasEq ?: (eq != null)
abstract class RsMetaItemImplMixin : RsStubbedElementImpl<RsMetaItemStub>, RsMetaItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsMetaItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val referenceNameElement: PsiElement get() = identifier

    override val referenceName: String get() = stub?.referenceName ?: referenceNameElement.text

    override fun getReference(): RsReference = RsMetaItemReferenceImpl(this)
}
