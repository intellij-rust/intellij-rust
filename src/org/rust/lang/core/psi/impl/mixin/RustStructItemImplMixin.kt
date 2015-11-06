package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Iconable
import org.rust.lang.core.psi.RustStructItem
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.core.psi.util.isPublic
import org.rust.lang.icons.RustIcons
import org.rust.lang.icons.addVisibilityIcon
import javax.swing.Icon

abstract  class RustStructItemImplMixin(node: ASTNode): RustItemImpl(node), RustStructItem {
    override fun getIcon(flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.CLASS;

        return RustIcons.CLASS.addVisibilityIcon(isPublic())
    }
}