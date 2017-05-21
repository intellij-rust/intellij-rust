package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.toolchain.RustToolchain

data class StdLibInfo (
    val name: String,
    val isRoot: Boolean = false,
    val srcDir: String = "lib" + name,
    val dependencies: List<String> = emptyList()
)

object AutoInjectedCrates {
    const val std: String = "std"
    const val core: String = "core"
    val stdlibCrates = listOf(
        // Roots
        StdLibInfo(std, dependencies = listOf("alloc_jemalloc", "alloc_system", "panic_abort", "rand",
            "compiler_builtins", "unwind", "rustc_asan", "rustc_lsan", "rustc_msan", "rustc_tsan",
            "build_helper"), isRoot = true),
        StdLibInfo(core, isRoot = true),
        StdLibInfo("alloc", isRoot = true),
        StdLibInfo("collections", isRoot = true),
        StdLibInfo("libc", srcDir = "liblibc/src", isRoot = true),
        StdLibInfo("panic_unwind", isRoot = true),
        StdLibInfo("rustc_unicode", isRoot = true),
        StdLibInfo("std_unicode", isRoot = true),
        StdLibInfo("test", dependencies = listOf("getopts", "term"), isRoot = true),
        // Dependencies
        StdLibInfo("alloc_jemalloc"),
        StdLibInfo("alloc_system"),
        StdLibInfo("build_helper", srcDir = "build_helper"),
        StdLibInfo("compiler_builtins"),
        StdLibInfo("getopts"),
        StdLibInfo("panic_unwind"),
        StdLibInfo("panic_abort"),
        StdLibInfo("rand"),
        StdLibInfo("rustc_asan"),
        StdLibInfo("rustc_lsan"),
        StdLibInfo("rustc_msan"),
        StdLibInfo("rustc_tsan"),
        StdLibInfo("term"),
        StdLibInfo("unwind")
    )
}

/**
 * Extracts Cargo based project's root-path (the one containing `Cargo.toml`)
 */
val Module.cargoProjectRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentRoots.firstOrNull {
        it.findChild(RustToolchain.CARGO_TOML) != null
    }
