package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Iconable
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustModItem
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.psi.util.isPublic
import org.rust.lang.icons.RustIcons
import org.rust.lang.icons.addVisibilityIcon
import javax.swing.Icon

abstract class RustModItemImplMixin(node: ASTNode) : RustItemImpl(node)
        , RustModItem {

    override fun getDeclarations(): Collection<RustDeclaringElement> =
        itemList

    override fun getIcon(flags: Int): Icon? {
        val icon = RustIcons.MODULE
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return icon;

        return icon.addVisibilityIcon(isPublic())
    }
}
