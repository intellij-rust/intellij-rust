package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustImplItemElement
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import javax.swing.Icon

abstract class RustImplItemImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustImplItemElement {

    override fun getIcon(flags: Int): Icon = RustIcons.IMPL
}
