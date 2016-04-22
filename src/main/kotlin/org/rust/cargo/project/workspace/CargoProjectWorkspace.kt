package org.rust.cargo.project.workspace

import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.toolchain.RustToolchain

/**
 * Uses `cargo metadata` command to update IDEA libraries and Cargo project model.
 */
interface CargoProjectWorkspace {

    /**
     * Updates Rust libraries asynchronously. Consecutive requests are coalesced.
     */
    fun requestUpdate(toolchain: RustToolchain, immediately: Boolean = false)

    /**
     * Latest version of the Cargo's project-description obtained
     *
     * NOTA BENE: In the current implementation it's SYNCHRONOUS
     */
    val projectDescription: CargoProjectDescription?

}
