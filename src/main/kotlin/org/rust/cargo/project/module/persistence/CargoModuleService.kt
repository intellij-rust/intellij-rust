package org.rust.cargo.project.module.persistence

import org.rust.cargo.CargoProjectDescription
import java.io.Serializable

/**
 * Stores information about crate roots (aka targets) of the module
 *
 * See [CargoProjectDataService] for how this is populated
 */
interface CargoModuleService {
    /**
     * Provides a set of targets for the corresponding module.
     *
     * You should prefer `Module.targets` extension instead.
     */
    val targets: Collection<CargoProjectDescription.Target>

    /**
     * Provides a set of external crates available in this module.
     *
     * You should prefer `Module.externalCrates` extension instead.
     */
    val externCrates: Collection<ExternCrateData>

    /**
     * Persists cargo related module data. This is done automatically on external project
     * refresh
     */
    fun saveData(targets: Collection<CargoProjectDescription.Target>,
                 externCrates: Collection<ExternCrateData>)
}
