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
                .mapNotNull { it.crateRoot }


val Module.preludeModule: PsiFile? get() {
    val stdlib = cargoProject?.findExternCrateRootByName(AutoInjectedCrates.std) ?: return null
    val preludeFile = stdlib.findFileByRelativePath("../prelude/v1.rs") ?: return null
    return project.getPsiFor(preludeFile)
}

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
    get() = getComponentOrThrow<CargoProjectWorkspace>().projectDescription?.let {
        extendProjectDescriptionWithStandardLibraryCrates(it)
    }
