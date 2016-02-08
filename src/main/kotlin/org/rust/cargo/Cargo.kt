package org.rust.cargo

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.rust.cargo.icons.CargoIcons

object Cargo {
    val ID      = "Cargo"
    val NAME    = "Cargo"

    val MANIFEST_FILE = "Cargo.toml"
    val LOCK_FILE     = "Cargo.lock"

    val PROJECT_SYSTEM_ID = ProjectSystemId(ID)
}
