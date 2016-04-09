package org.rust.cargo.toolchain

import org.rust.cargo.CargoProjectDescription


/*
 * Uses `cargo metadata` command to update IDEA libraries and Cargo project model.
 */
interface CargoMetadataService {
    /**
     * Updates Rust libraries asynchronously. Consecutive updates are coalesced.
     */
    fun scheduleUpdate(toolchain: RustToolchain)
    val cargoProject: CargoProjectDescription?
}
