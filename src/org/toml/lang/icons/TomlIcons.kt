package org.toml.lang.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader


public object TomlIcons {
    public val FILE = AllIcons.FileTypes.Text

    public val CARGO        = IconLoader.getIcon("/org/toml/icons/cargo.png")
    public val CARGO_LOCK   = IconLoader.getIcon("/org/toml/icons/cargo-lock.png")
}


