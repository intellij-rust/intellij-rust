package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsForeignModItem
import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.stubs.RsPlaceholderStub

abstract class RsForeignModItemImplMixin : RsStubbedElementImpl<RsPlaceholderStub>,
                                           RsForeignModItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RsPlaceholderStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override val outerAttrList: List<RsOuterAttr>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RsOuterAttr::class.java)

    override val isPublic: Boolean get() = false // visibility does not affect foreign mods
}
