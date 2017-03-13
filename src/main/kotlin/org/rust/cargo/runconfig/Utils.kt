package org.rust.cargo.runconfig

import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.toolchain.CargoCommandLine

val CargoWorkspace.Target.cargoArgumentSpeck: List<String> get() {
    // Can get fancy here not add this arguments if we are the root package
    val pkgSpec = listOf("--package", pkg.name)

    val targetSpec = when (kind) {
        CargoWorkspace.TargetKind.BIN -> listOf("--bin", name)
        CargoWorkspace.TargetKind.TEST -> listOf("--test", name)
        CargoWorkspace.TargetKind.EXAMPLE -> listOf("--example", name)
        CargoWorkspace.TargetKind.BENCH -> listOf("--bench", name)
        CargoWorkspace.TargetKind.LIB -> listOf("--lib")
        CargoWorkspace.TargetKind.UNKNOWN -> return pkgSpec
    }

    return pkgSpec + targetSpec
}

fun CargoCommandLine.mergeWithDefault(default: CargoCommandLine): CargoCommandLine =
    if (environmentVariables.isEmpty())
        copy(environmentVariables = default.environmentVariables)
    else
        this

