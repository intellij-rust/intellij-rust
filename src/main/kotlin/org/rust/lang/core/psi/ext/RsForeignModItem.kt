/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.macros.RsExpandedElement
import org.rust.lang.core.psi.RsForeignModItem
import org.rust.lang.core.psi.RsInnerAttr
import org.rust.lang.core.stubs.RsPlaceholderStub

abstract class RsForeignModItemImplMixin : RsStubbedElementImpl<RsPlaceholderStub>,
                                           RsForeignModItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPlaceholderStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override val innerAttrList: List<RsInnerAttr>
        get() = stubChildrenOfType()

    override val visibility: RsVisibility get() = RsVisibility.Private // visibility does not affect foreign mods

    override fun getContext(): PsiElement? = RsExpandedElement.getContextImpl(this)
}
