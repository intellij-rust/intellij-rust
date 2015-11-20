package org.rust.cargo

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.util.IconLoader

object Cargo {
    val ID = "Cargo"
    val NAME = "Cargo"
    val BUILD_FILE = "Cargo.toml"
    val LOCK_FILE = "Cargo.lock"
    val ICON = IconLoader.getIcon("/org/toml/icons/cargo16.png")

    val PROJECT_SYSTEM_ID = ProjectSystemId(ID)
}
