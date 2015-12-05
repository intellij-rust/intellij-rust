package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustAnonModItem
import org.rust.lang.core.psi.impl.RustModItemImpl
import org.rust.lang.icons.addVisibilityIcon
import javax.swing.Icon

public abstract class RustAnonModItemImplMixin(node: ASTNode) : RustModItemImpl(node)
                                                                , RustAnonModItem {

    override fun getIcon(flags: Int): Icon? =
        super.getIcon(flags)?.let {
            it.addVisibilityIcon(true)
        }
}

