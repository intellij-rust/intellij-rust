package org.rust.lang.core.psi

import com.intellij.openapi.util.Iconable
import com.intellij.psi.impl.ElementBase
import org.rust.ide.icons.RustIcons
import org.rust.ide.icons.addVisibilityIcon
import javax.swing.Icon

interface RustAccessControlElement : RustCompositeElement {
    val isPublic: Boolean
}

fun RustAccessControlElement.iconWithVisibility(flags: Int, icon: Icon): Icon =
    if ((flags and Iconable.ICON_FLAG_VISIBILITY) == 0)
        icon
    else
        icon.addVisibilityIcon(isPublic)
