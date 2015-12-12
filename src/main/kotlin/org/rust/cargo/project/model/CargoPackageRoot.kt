package org.rust.cargo.project.model

data class CargoPackageRoot(
    val name:       String,
    val version:    String,
    val features:   Map<String, List<String>>?
)
