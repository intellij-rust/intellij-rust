package org.rust.cargo.project

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import org.rust.cargo.CargoConstants

object CargoProjectSystem {
    val ID = ProjectSystemId(CargoConstants.ID, CargoConstants.NAME)
}
