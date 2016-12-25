package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.toolchain.RustToolchain


object AutoInjectedCrates {
    const val std: String = "std"
    const val core: String = "core"
    val stdlibCrateNames = listOf(std, core, "collections", "alloc", "rustc_unicode", "std_unicode")
}

/**
 * Extracts Cargo based project's root-path (the one containing `Cargo.toml`)
 */
val Module.cargoProjectRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentRoots.firstOrNull {
        it.findChild(RustToolchain.CARGO_TOML) != null
    }

