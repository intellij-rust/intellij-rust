package org.rust.cargo.project.workspace

import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.toolchain.RustToolchain
import java.util.concurrent.Future

/**
 * Uses `cargo metadata` command to update IDEA libraries and Cargo project model.
 */
interface CargoProjectWorkspace {
    /**
     * Updates Rust libraries asynchronously. Consecutive updates are coalesced.
     */
    fun scheduleUpdate(toolchain: RustToolchain): Future<CargoProjectDescription>

    /**
     * Immediately schedules an update. Shows balloon upon completion.
     *
     * Update is still asynchronous.
     */
    fun updateNow(toolchain: RustToolchain): Future<CargoProjectDescription>

    val projectDescription: CargoProjectDescription?
}
