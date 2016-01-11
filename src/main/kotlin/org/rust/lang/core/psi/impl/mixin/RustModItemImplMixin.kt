package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Iconable
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustModItem
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addVisibilityIcon
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon

abstract class RustModItemImplMixin : RustItemImpl
                                    , RustModItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val declarations: Collection<RustDeclaringElement>
        get() = itemList

    override fun getIcon(flags: Int): Icon? {
        val icon = RustIcons.MODULE
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return icon;

        return icon.addVisibilityIcon(isPublic)
    }
}
