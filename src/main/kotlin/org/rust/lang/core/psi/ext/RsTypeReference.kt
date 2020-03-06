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


val RsTypeReference.typeElement: RsTypeReference
    get() = unwrapParens()

private tailrec fun RsTypeReference.unwrapParens(): RsTypeReference {
    return if (this !is RsParenType) {
        this
    } else {
        val typeReference = typeReference ?: return this
        typeReference.unwrapParens()
    }
}

val RsTypeReference.owner: RsTypeReference
    get() = ancestors
        .filterNot { it is RsTypeArgumentList || it is RsPath }
        .takeWhile { it is RsBaseType || it is RsTupleType || it is RsRefLikeType || it is RsTypeReference }
        .last() as RsTypeReference

abstract class RsTypeReferenceImplMixin : RsStubbedElementImpl<StubBase<*>>, RsTypeReference {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubBase<*>, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}
