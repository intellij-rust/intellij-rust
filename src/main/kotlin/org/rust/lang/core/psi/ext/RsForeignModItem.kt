/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.macros.ExpansionResult
import org.rust.lang.core.psi.RsForeignModItem
import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.stubs.RsPlaceholderStub

abstract class RsForeignModItemImplMixin : RsStubbedElementImpl<RsPlaceholderStub>,
                                           RsForeignModItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPlaceholderStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override val outerAttrList: List<RsOuterAttr>
        get() = childrenOfType()

    override val isPublic: Boolean get() = false // visibility does not affect foreign mods

    override fun getContext() = ExpansionResult.getContextImpl(this)
}
