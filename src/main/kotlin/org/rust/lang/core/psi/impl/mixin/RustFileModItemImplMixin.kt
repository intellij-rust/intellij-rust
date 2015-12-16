package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.RustFileType
import org.rust.lang.core.psi.RustFileModItem
import org.rust.lang.core.psi.impl.RustModItemImpl
import org.rust.lang.core.psi.util.RustModules
import org.rust.ide.icons.addVisibilityIcon
import javax.swing.Icon

public abstract class RustFileModItemImplMixin(node: ASTNode) : RustModItemImpl(node)
                                                              , RustFileModItem {

    override fun getName(): String? =
        containingFile.let { file ->
            when (file.name) {
                RustModules.MOD_RS -> file.parent?.name
                else               -> file.name.removeSuffix(RustFileType.DEFAULTS.EXTENSION)
            }
        }

    override fun getIcon(flags: Int): Icon? =
        super.getIcon(flags)?.let {
            it.addVisibilityIcon(true)
        }
}

