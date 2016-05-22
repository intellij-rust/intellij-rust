package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.workspace.CargoProjectWorkspace
import org.rust.cargo.toolchain.RustToolchain

object RustCrateUtil


/**
 * Extracts paths to the Crate's roots'
 */
val Module.crateRoots: Collection<VirtualFile>
    get() = cargoProject?.packages.orEmpty()
                .flatMap    { it.targets }
                .mapNotNull { it.crateRoot } + standardLibraryCrates.map { it.virtualFile }


data class ExternCrate(
    /**
     * Name of a crate as appears in `extern crate foo;`
     */
    val name: String,

    /**
     * Root module file (typically `src/lib.rs`)
     */
    val virtualFile: VirtualFile
)

/**
 * Searches for the `PsiFile` of the root mod of the crate
 */
fun Module.findExternCrateRootByName(crateName: String): VirtualFile? =
    externCrates.find { it.name == crateName } ?.let { it.virtualFile }

val Module.preludeModule: PsiFile? get() {
    val stdlib = findExternCrateRootByName(AutoInjectedCrates.std) ?: return null
    val preludeFile = stdlib.findFileByRelativePath("../prelude/v1.rs") ?: return null
    return project.getPsiFor(preludeFile)
}

/**
 * A set of external crates for the module. External crate can refer
 * to another module or a library or a crate form the SDK
 */
internal val Module.externCrates: Collection<ExternCrate> get() =
    cargoProject?.packages.orEmpty().mapNotNull { pkg ->
        pkg.libTarget?.crateRoot?.let { ExternCrate(pkg.name, it) }
    } + standardLibraryCrates

object AutoInjectedCrates {
    const val std: String = "std"
    const val core: String = "core"
}

/**
 * Extracts Cargo based project's root-path (the one containing `Cargo.toml`)
 */
val Module.cargoProjectRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentRoots.firstOrNull {
        it.findChild(RustToolchain.CARGO_TOML) != null
    }

/**
 * Extracts Cargo project description out of `Cargo.toml`
 */
val Module.cargoProject: CargoProjectDescription?
    get() = getComponentOrThrow<CargoProjectWorkspace>().projectDescription
