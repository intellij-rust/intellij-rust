/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.toolchain.RustToolchain

enum class StdLibType {
    /**
     * An indispensable part of the stdlib
     */
    ROOT,

    /**
     * A crate that can be used as a dependency if a corresponding feature is turned on
     */
    FEATURE_GATED,

    /**
     * A dependency that is not visible outside of the stdlib
     */
    DEPENDENCY
}

data class StdLibInfo (
    val name: String,
    val type: StdLibType,
    val srcDir: String = "lib" + name,
    val dependencies: List<String> = emptyList()
)

object AutoInjectedCrates {
    const val std: String = "std"
    const val core: String = "core"
    val stdlibCrates = listOf(
        // Roots
        StdLibInfo(std, StdLibType.ROOT, dependencies = listOf("alloc_jemalloc", "alloc_system", "panic_abort", "rand",
            "compiler_builtins", "unwind", "rustc_asan", "rustc_lsan", "rustc_msan", "rustc_tsan",
            "build_helper")),
        StdLibInfo(core, StdLibType.ROOT),
        StdLibInfo("alloc", StdLibType.ROOT),
        StdLibInfo("collections", StdLibType.ROOT),
        StdLibInfo("libc", StdLibType.ROOT, srcDir = "liblibc/src"),
        StdLibInfo("panic_unwind", type = StdLibType.ROOT),
        StdLibInfo("proc_macro", type = StdLibType.ROOT),
        StdLibInfo("rustc_unicode", type = StdLibType.ROOT),
        StdLibInfo("std_unicode", type = StdLibType.ROOT),
        StdLibInfo("test", dependencies = listOf("getopts", "term"), type = StdLibType.ROOT),
        // Feature gated
        StdLibInfo("alloc_jemalloc", StdLibType.FEATURE_GATED),
        StdLibInfo("alloc_system", StdLibType.FEATURE_GATED),
        StdLibInfo("compiler_builtins", StdLibType.FEATURE_GATED),
        StdLibInfo("getopts", StdLibType.FEATURE_GATED),
        StdLibInfo("panic_unwind", StdLibType.FEATURE_GATED),
        StdLibInfo("panic_abort", StdLibType.FEATURE_GATED),
        StdLibInfo("rand", StdLibType.FEATURE_GATED),
        StdLibInfo("term", StdLibType.FEATURE_GATED),
        StdLibInfo("unwind", StdLibType.FEATURE_GATED),
        // Dependencies
        StdLibInfo("build_helper", StdLibType.DEPENDENCY, srcDir = "build_helper"),
        StdLibInfo("rustc_asan", StdLibType.DEPENDENCY),
        StdLibInfo("rustc_lsan", StdLibType.DEPENDENCY),
        StdLibInfo("rustc_msan", StdLibType.DEPENDENCY),
        StdLibInfo("rustc_tsan", StdLibType.DEPENDENCY),
        StdLibInfo("syntax", StdLibType.DEPENDENCY)
    )
}

/**
 * Extracts Cargo based project's root-path (the one containing `Cargo.toml`)
 */
val Module.cargoProjectRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentRoots.firstOrNull {
        it.findChild(RustToolchain.CARGO_TOML) != null
    }
