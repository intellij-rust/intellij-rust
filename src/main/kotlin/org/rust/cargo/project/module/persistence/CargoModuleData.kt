package org.rust.cargo.project.module.persistence

import org.rust.cargo.CargoProjectDescription
import java.io.Serializable

data class CargoModuleData(
    val targets: Collection<CargoProjectDescription.Target>,
    val externCrates: Collection<ExternCrateData>
) : Serializable


data class ExternCrateData(
    /**
     * name of the crate as appears in `extern crate foo;`
     */
    val name: String,

    /**
     * Path to the root module of a crate.
     *
     * Absolute if this crate is a library from crates.io.
     * Relative to the module root if this is a crate from another module (path dependency).
     */
    val path: String
) : Serializable

