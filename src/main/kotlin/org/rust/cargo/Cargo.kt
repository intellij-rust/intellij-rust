package org.rust.cargo

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.rust.cargo.icons.CargoIcons

object Cargo {
    val ID      = "Cargo"
    val NAME    = "Cargo"

    val BUILD_FILE  = "Cargo.toml"
    val LOCK_FILE   = "Cargo.lock"

    val ICON by lazy { CargoIcons.ICON }

    val PROJECT_SYSTEM_ID = ProjectSystemId(ID)
}
