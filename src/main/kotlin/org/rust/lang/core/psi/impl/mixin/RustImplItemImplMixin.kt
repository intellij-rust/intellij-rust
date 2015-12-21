package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.impl.RustItemImpl
import org.rust.ide.icons.RustIcons
import javax.swing.Icon

abstract class RustImplItemImplMixin(node: ASTNode) : RustItemImpl(node) {
    override fun getIcon(flags: Int): Icon? {
        return RustIcons.IMPL
    }
}
