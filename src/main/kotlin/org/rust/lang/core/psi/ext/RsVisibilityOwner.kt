package org.rust.lang.core.psi.ext

import org.rust.ide.icons.addVisibilityIcon
import org.rust.lang.core.psi.RsVis
import javax.swing.Icon

interface RsVisibilityOwner : RsCompositeElement {
    val vis: RsVis?
    val isPublic: Boolean
}

fun RsVisibilityOwner.iconWithVisibility(flags: Int, icon: Icon): Icon =
    if ((flags and com.intellij.openapi.util.Iconable.ICON_FLAG_VISIBILITY) == 0)
        icon
    else
        icon.addVisibilityIcon(isPublic)
