package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addStaticMark
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustImplMethod
import org.rust.lang.core.psi.iconWithVisibility
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import javax.swing.Icon

abstract class RustImplMethodImplMixin(node: ASTNode)   : RustNamedElementImpl(node)
                                                        , RustImplMethod {

    override val declarations: Collection<RustDeclaringElement>
        get() = paramList.orEmpty().filterNotNull()

    override fun getIcon(flags: Int): Icon? {
        var icon = RustIcons.METHOD
        if (isStatic) {
            icon = icon.addStaticMark()
        }
        return iconWithVisibility(flags, icon)
    }

    override val isPublic: Boolean
        get() = vis != null

    val isStatic: Boolean
        get() = self == null
}
