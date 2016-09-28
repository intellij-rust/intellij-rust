package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addTestMark
import org.rust.lang.core.psi.RustFnItemElement
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.stubs.elements.RustFnItemElementStub
import javax.swing.Icon

abstract class RustFnItemImplMixin : RustFnImplMixin<RustFnItemElementStub>,
                                     RustFnItemElement {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustFnItemElementStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon {
        var icon = RustIcons.FUNCTION
        if (isTest) {
            icon = icon.addTestMark()
        }
        return iconWithVisibility(flags, icon)
    }

    override val isPublic: Boolean get() = RustPsiImplUtil.isPublic(this)

}

