package org.rust.cargo.project.workspace

import org.rust.cargo.project.CargoProjectDescription

/**
 * Interface to subscribe for the Cargo-backed project updates. That's a rather low-level API
 */
interface CargoProjectWorkspaceListener {

    /**
     * Called every time Cargo's project gets description updated, no
     * matter whether did it actually changed from the previous update or not
     */
    fun onProjectUpdated(projectDescription: CargoProjectDescription)

}
