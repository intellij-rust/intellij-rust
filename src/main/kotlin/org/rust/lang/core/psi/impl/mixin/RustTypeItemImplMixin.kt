package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustTypeItemElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.elements.RustTypeItemStub
import javax.swing.Icon

abstract class RustTypeItemImplMixin : RustStubbedNamedElementImpl<RustTypeItemStub>, RustTypeItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustTypeItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? {
        return iconWithVisibility(flags, RustIcons.TYPE)
    }
}
