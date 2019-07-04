/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.stubs.RsPlaceholderStub


val RsTypeReference.typeElement: RsTypeElement?
    get() = PsiTreeUtil.getStubChildOfType(this, RsTypeElement::class.java)

abstract class RsTypeReferenceImplMixin : RsStubbedElementImpl<RsPlaceholderStub>, RsTypeReference {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPlaceholderStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}
