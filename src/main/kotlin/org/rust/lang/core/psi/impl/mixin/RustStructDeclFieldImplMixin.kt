package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Iconable
import org.rust.lang.core.psi.RustStructDeclField
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addVisibilityIcon
import javax.swing.Icon

abstract class RustStructDeclFieldImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustStructDeclField {
    override fun getIcon(flags: Int): Icon? {
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return RustIcons.FIELD;

        return RustIcons.FIELD.addVisibilityIcon(isPublic)
    }

    val isPublic: Boolean
        get() = vis != null;

}
