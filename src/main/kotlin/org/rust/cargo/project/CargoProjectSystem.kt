package org.rust.cargo.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.rust.cargo.Cargo

object CargoProjectSystem {
    val ID = ProjectSystemId(Cargo.ID, Cargo.NAME)
}
