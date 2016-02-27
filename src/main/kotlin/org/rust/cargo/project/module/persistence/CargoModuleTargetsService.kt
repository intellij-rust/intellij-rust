package org.rust.cargo.project.module.persistence

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import org.rust.cargo.CargoProjectDescription

/**
 * Stores information about crate roots (aka targets) of the module
 *
 * See [CargoProjectDataService] for how this is populated
 */
interface CargoModuleTargetsService {
    /**
     * Provides a set of targets for the corresponding module.
     *
     * You should prefer `Module.targets` extensions instead
     */
    val targets: Collection<CargoProjectDescription.Target>

    /**
     * Persists new set of targets. This is done automatically on external project
     * refresh
     */
    fun saveTargets(targets: Collection<CargoProjectDescription.Target>)
}
