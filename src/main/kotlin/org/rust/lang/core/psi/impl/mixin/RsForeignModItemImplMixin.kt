package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.RsForeignModItem
import org.rust.lang.core.psi.RsOuterAttr
import org.rust.lang.core.psi.impl.RsStubbedElementImpl
import org.rust.lang.core.stubs.RustPlaceholderStub

abstract class RsForeignModItemImplMixin : RsStubbedElementImpl<RustPlaceholderStub>,
                                           RsForeignModItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustPlaceholderStub, elementType: IStubElementType<*, *>) : super(stub, elementType)

    override val outerAttrList: List<RsOuterAttr>
        get() = PsiTreeUtil.getChildrenOfTypeAsList(this, RsOuterAttr::class.java)

    override val isPublic: Boolean get() = false // visibility does not affect foreign mods
}
