package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Iconable
import org.rust.lang.core.psi.RustEnumItem
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.lang.icons.RustIcons
import org.rust.lang.icons.addVisibilityIcon
import javax.swing.Icon


abstract class RustEnumItemImplMixin(node: ASTNode) : RustItemImpl(node), RustEnumItem {
    override fun getIcon(flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.ENUM;

        return RustIcons.ENUM.addVisibilityIcon(isPublic())

    }
}