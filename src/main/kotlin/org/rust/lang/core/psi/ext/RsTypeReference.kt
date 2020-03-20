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


/**
 * Any type can be wrapped into parens, e.g. `let a: (i32) = 1;` Such type is parsed as [RsParenType].
 * This method unwraps any number of parens around the type.
 */
tailrec fun RsTypeReference.skipParens(): RsTypeReference {
    if (this !is RsParenType) return this
    val typeReference = typeReference ?: return this
    return typeReference.skipParens()
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
