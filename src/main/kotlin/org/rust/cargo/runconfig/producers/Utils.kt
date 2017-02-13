package org.rust.cargo.runconfig.producers

import org.rust.cargo.project.workspace.CargoWorkspace

val CargoWorkspace.Target.cargoArgumentSpeck: String get() {
    // Can get fancy here not add this arguments if we are the root package
    val pkgSpec = "--package ${pkg.name}"

    val targetSpec = when (kind) {
        CargoWorkspace.TargetKind.BIN -> "--bin $name"
        CargoWorkspace.TargetKind.TEST -> "--test $name"
        CargoWorkspace.TargetKind.EXAMPLE -> "--example $name"
        CargoWorkspace.TargetKind.BENCH -> "--bench $name"
        CargoWorkspace.TargetKind.LIB -> "--lib"
        CargoWorkspace.TargetKind.UNKNOWN -> return pkgSpec
    }

    return "$pkgSpec $targetSpec"
}


