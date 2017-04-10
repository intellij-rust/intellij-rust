package org.rust.cargo.project.workspace

import com.intellij.execution.ExecutionException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.toolchain.RustToolchain

/**
 * Cargo based project's workspace abstraction insulating inter-op with the `cargo` & `Cargo.toml`
 * itself. Uses `cargo metadata` sub-command to update IDEA module's & project's model.
 *
 * Quite low-level in its nature & follows a soft-fail policy, i.e. provides access
 * for latest obtained instance of the [CargoWorkspace], though doesn't assure that this
 * one could be obtained (consider the case with invalid, or missing `Cargo.toml`)
 *
 * Updating project information is inherently asynchronous, however during the tests
 * all updates are executed synchronously on the EDT thread. This is needed because
 * the tests themselves use EDT, so any async update which tries to commit via a
 * WriteAction leads to a deadlock.
 */
interface CargoProjectWorkspaceService {

    /**
     * Updates Rust libraries asynchronously. Consecutive requests are coalesced.
     */
    fun requestUpdate(toolchain: RustToolchain)

    /**
     * Schedules an update without debouncing. The update won't start immediately
     * if there is one currently executing. [afterCommit] callback is called on the
     * EDT thread.
     */
    fun requestImmediateUpdate(toolchain: RustToolchain, afterCommit: (UpdateResult) -> Unit)

    @TestOnly
    fun syncUpdate(toolchain: RustToolchain)

    /**
     * Latest version of the Cargo's project-description obtained
     */
    val workspace: CargoWorkspace?

    sealed class UpdateResult {
        class Ok(val workspace: CargoWorkspace) : UpdateResult()
        class Err(val error: ExecutionException) : UpdateResult()
    }

    companion object {

        fun getInstance(module: Module): CargoProjectWorkspaceService =
            ModuleServiceManager.getService(module, CargoProjectWorkspaceService::class.java)
                ?: error("Failed to retrieve CargoProjectWorkspaceService for $module")
    }
}

/**
 * Extracts Cargo project description out of `Cargo.toml`
 */
val Module.cargoWorkspace: CargoWorkspace?
    get() = CargoProjectWorkspaceService.getInstance(this).workspace
