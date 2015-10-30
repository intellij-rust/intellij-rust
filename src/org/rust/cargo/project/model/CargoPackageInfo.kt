package org.rust.cargo.project.model

class CargoPackageInfo(
        val name: String,
        val version: String,
        val manifest_path: String,
        val dependencies: List<CargoPackageRef>,
        val targets: List<CargoTarget>)