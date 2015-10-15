package org.rust.lang.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * @author Evgeny.Kurbatsky
 */
public object RustIcons {

    public val NORMAL:  Icon = IconLoader.getIcon("/org/rust/icons/rust16.png")
    public val BIG:     Icon = IconLoader.getIcon("/org/rust/icons/rust32.png")

    public val FUNCTION = AllIcons.Nodes.Function
    public val METHOD   = AllIcons.Nodes.Method
}
