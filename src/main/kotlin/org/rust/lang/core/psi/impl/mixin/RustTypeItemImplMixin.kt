package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustTypeItemElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustStubbedNamedVisibilityOwnerElementImpl
import org.rust.lang.core.stubs.elements.RustTypeItemElementStub
import javax.swing.Icon

abstract class RustTypeItemImplMixin : RustStubbedNamedVisibilityOwnerElementImpl<RustTypeItemElementStub>, RustTypeItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustTypeItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? {
        return iconWithVisibility(flags, RustIcons.TYPE)
    }
}
