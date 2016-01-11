package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Iconable
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addVisibilityIcon
import org.rust.lang.core.psi.RustTraitItem
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon


abstract class RustTraitItemImplMixin : RustItemImpl, RustTraitItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.TRAIT;

        return RustIcons.TRAIT.addVisibilityIcon(isPublic)
    }
}
