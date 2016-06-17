package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustEnumItemElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustStubbedNamedVisibilityOwnerElementImpl
import org.rust.lang.core.stubs.elements.RustEnumItemElementStub
import javax.swing.Icon


abstract class RustEnumItemImplMixin : RustStubbedNamedVisibilityOwnerElementImpl<RustEnumItemElementStub>, RustEnumItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustEnumItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? =
        iconWithVisibility(flags, RustIcons.ENUM)

}
