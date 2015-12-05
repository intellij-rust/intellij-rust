package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.lang.core.psi.RustEnumDef
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.icons.RustIcons
import javax.swing.Icon


abstract class RustEnumDefImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustEnumDef {
    override fun getIcon(flags: Int): Icon? {
        return RustIcons.FIELD
    }
}