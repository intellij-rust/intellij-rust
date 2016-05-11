package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.ide.icons.RustIcons
import org.rust.lang.core.psi.RustStructDeclField
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import javax.swing.Icon

abstract class RustStructDeclFieldImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustStructDeclField {

    override fun getIcon(flags: Int): Icon =
        iconWithVisibility(flags, RustIcons.FIELD)

}
