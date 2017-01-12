package org.rust.cargo.runconfig.producers

import org.rust.cargo.project.CargoProjectDescription

val CargoProjectDescription.Target.cargoArgumentSpeck: String get() {
    // Can get fancy here not add this arguments if we are the root package
    val pkgSpec = "--package ${pkg.name}"

    val targetSpec = when (kind) {
        CargoProjectDescription.TargetKind.BIN -> "--bin ${name}"
        CargoProjectDescription.TargetKind.TEST -> "--test ${name}"
        CargoProjectDescription.TargetKind.EXAMPLE -> "--example ${name}"
        CargoProjectDescription.TargetKind.BENCH -> "--bench ${name}"
        CargoProjectDescription.TargetKind.LIB -> "--lib"
        CargoProjectDescription.TargetKind.UNKNOWN -> return pkgSpec
    }

    return "$pkgSpec $targetSpec"
}


