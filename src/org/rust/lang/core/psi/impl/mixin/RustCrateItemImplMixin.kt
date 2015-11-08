package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustCrateItem
import org.rust.lang.core.psi.impl.RustModItemImpl
import org.rust.lang.icons.addVisibilityIcon
import javax.swing.Icon

public abstract class RustCrateItemImplMixin(node: ASTNode) : RustModItemImpl(node)
                                                            , RustCrateItem {

    override fun getIcon(flags: Int): Icon? =
        super.getIcon(flags)?.let {
            it.addVisibilityIcon(true)
        }
}

