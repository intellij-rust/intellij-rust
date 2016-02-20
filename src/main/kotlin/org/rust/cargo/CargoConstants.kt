package org.rust.cargo

import com.intellij.openapi.externalSystem.model.ProjectSystemId

object CargoConstants {
    const val ID      = "Cargo"
    const val NAME    = "Cargo"

    const val MANIFEST_FILE = "Cargo.toml"
    const val LOCK_FILE     = "Cargo.lock"

    val PROJECT_SYSTEM_ID = ProjectSystemId(ID)

    const val CARGO_EXECUTABLE_NAME: String = "cargo"
}
