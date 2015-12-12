package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustFileModItem
import org.rust.lang.core.psi.impl.RustModItemImpl
import org.rust.lang.icons.addVisibilityIcon
import javax.swing.Icon

public abstract class RustFileModItemImplMixin(node: ASTNode) : RustModItemImpl(node)
                                                              , RustFileModItem {


    override fun getIcon(flags: Int): Icon? =
        super.getIcon(flags)?.let {
            it.addVisibilityIcon(true)
        }
}

