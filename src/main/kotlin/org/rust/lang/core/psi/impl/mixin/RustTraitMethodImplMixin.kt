package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Iconable
import org.rust.lang.core.psi.RustTraitMethod
import org.rust.lang.core.psi.RustTypeMethod
import org.rust.lang.core.psi.impl.RustCompositeElementImpl
import org.rust.lang.icons.*
import javax.swing.Icon


abstract class RustTraitMethodImplMixin(node: ASTNode) : RustCompositeElementImpl(node), RustTraitMethod {
    override fun getIcon(flags: Int): Icon? {
        var icon = if (isAbstract()) RustIcons.ABSTRACT_METHOD else RustIcons.METHOD
        if (isStatic())
            icon = icon.addStaticMark()

        if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
            return icon;

        return icon.addVisibilityIcon(isPublic())
    }

    fun isPublic(): Boolean {
        return vis != null;
    }

    fun isAbstract(): Boolean {
        return this is RustTypeMethod;
    }

    fun isStatic(): Boolean {
        return self == null;
    }
}