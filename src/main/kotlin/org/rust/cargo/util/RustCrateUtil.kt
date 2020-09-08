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

data class StdLibInfo(
    val name: String,
    val type: StdLibType,
    val dependencies: List<String> = emptyList()
)

object AutoInjectedCrates {
    const val STD: String = "std"
    const val CORE: String = "core"
    val stdlibCrates = listOf(
        // Roots
        StdLibInfo(CORE, StdLibType.ROOT),
        StdLibInfo(STD, StdLibType.ROOT, dependencies = listOf("alloc", "panic_unwind", "panic_abort",
            CORE, "libc", "compiler_builtins", "profiler_builtins", "unwind")),
        StdLibInfo("alloc", StdLibType.ROOT, dependencies = listOf(CORE, "compiler_builtins")),
        StdLibInfo("proc_macro", type = StdLibType.ROOT, dependencies = listOf(STD)),
        StdLibInfo("test", type = StdLibType.ROOT, dependencies = listOf(STD, CORE, "libc", "getopts", "term")),
        // Feature gated
        StdLibInfo("libc", StdLibType.FEATURE_GATED),
        StdLibInfo("panic_unwind", type = StdLibType.FEATURE_GATED, dependencies = listOf(CORE, "libc", "alloc",
            "unwind", "compiler_builtins")),
        StdLibInfo("compiler_builtins", StdLibType.FEATURE_GATED, dependencies = listOf(CORE)),
        StdLibInfo("profiler_builtins", StdLibType.FEATURE_GATED, dependencies = listOf(CORE, "compiler_builtins")),
        StdLibInfo("panic_abort", StdLibType.FEATURE_GATED, dependencies = listOf(CORE, "libc", "compiler_builtins")),
        StdLibInfo("unwind", StdLibType.FEATURE_GATED, dependencies = listOf(CORE, "libc", "compiler_builtins")),
        StdLibInfo("term", StdLibType.FEATURE_GATED, dependencies = listOf(STD, CORE)),
        StdLibInfo("getopts", StdLibType.FEATURE_GATED, dependencies = listOf(STD, CORE)),
    )
}

/**
 * Extracts Cargo based project's root-path (the one containing `Cargo.toml`)
 */
val Module.cargoProjectRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentRoots.firstOrNull {
        it.findChild(RustToolchain.CARGO_TOML) != null
    }
