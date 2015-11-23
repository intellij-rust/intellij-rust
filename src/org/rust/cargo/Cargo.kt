package org.rust.cargo

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.toml.lang.icons.TomlIcons

object Cargo {
    val ID          = "Cargo"
    val NAME        = "Cargo"

    val BUILD_FILE  = "Cargo.toml"
    val LOCK_FILE   = "Cargo.lock"

    val ICON by lazy { TomlIcons.CARGO_FILE }

    val PROJECT_SYSTEM_ID = ProjectSystemId(ID)
}
