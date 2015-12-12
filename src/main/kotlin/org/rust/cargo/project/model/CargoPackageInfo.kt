package org.rust.cargo.project.model

/**
 * This class is merely a wrapper of the metadata for the package extracted
 * out of `Cargo.toml`
 *
 * NOTE:
 *      Params are named accordingly to the output of the `cargo metadata`
 *      therefore should stay consistent, and remain in snake-form
 */
data class CargoPackageInfo(
    val name:           String,
    val version:        String,
    val manifest_path:  String,
    val dependencies:   List<CargoPackageRef>,
    val targets:        List<CargoTarget>
)
