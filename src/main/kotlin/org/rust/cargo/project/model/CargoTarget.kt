package org.rust.cargo.project.model


/**
 * This class is merely a wrapper of the metadata for the project extracted
 * out of `Cargo.toml`
 *
 * NOTE:
 *      Params are named accordingly to the output of the `cargo metadata`
 *      therefore should stay consistent, and remain in snake-form
 */
data class CargoTarget(
    val name:       String,
    val kind:       List<String>,
    val src_path:   String,
    val metadata:   Map<String, String>
)
