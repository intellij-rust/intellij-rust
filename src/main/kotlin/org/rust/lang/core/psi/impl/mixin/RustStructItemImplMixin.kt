package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustStructItemElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.stubs.elements.RustStructItemStub
import javax.swing.Icon

abstract class RustStructItemImplMixin : RustStubbedNamedElementImpl<RustStructItemStub>, RustStructItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustStructItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RustIcons.STRUCT)
}
