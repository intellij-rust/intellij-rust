package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Iconable
import org.rust.lang.core.psi.RustDeclaringElement
import org.rust.lang.core.psi.RustImplMethod
import org.rust.lang.core.psi.impl.RustNamedElementImpl
import org.rust.lang.icons.*
import javax.swing.Icon

abstract class RustImplMethodImplMixin(node: ASTNode)   : RustNamedElementImpl(node)
                                                        , RustImplMethod {

    override fun getDeclarations(): Collection<RustDeclaringElement> =
        paramList.orEmpty().filterNotNull()

    override fun getIcon(flags: Int): Icon? {
        val icon = if (isStatic()) RustIcons.METHOD.addStaticMark() else RustIcons.METHOD
        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return icon;

        return icon.addVisibilityIcon(isPublic())
    }

    fun isPublic(): Boolean {
        return vis != null;
    }

    fun isStatic(): Boolean {
        return self == null;
    }
}