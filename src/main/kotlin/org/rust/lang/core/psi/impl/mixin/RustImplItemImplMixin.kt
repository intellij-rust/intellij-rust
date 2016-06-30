package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.RustPathTypeElement
import org.rust.lang.core.psi.impl.RustStubbedElementImpl
import org.rust.lang.core.psi.referenceName
import org.rust.lang.core.stubs.elements.RustImplItemElementStub
import javax.swing.Icon

abstract class RustImplItemImplMixin : RustStubbedElementImpl<RustImplItemElementStub>, RustImplItemElement {
    constructor(node: ASTNode) : super(node)

    constructor(stub: RustImplItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon = RustIcons.IMPL

    override val isPublic: Boolean get() = false // pub does not affect imls at all
}


val RustImplItemElement.baseTypeName: String? get() {
    //TODO: move to `RustTypeElement`
    return (type as? RustPathTypeElement)?.path?.referenceName
}
