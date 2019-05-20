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
    val srcDir: String = "lib$name"
)

object AutoInjectedCrates {
    const val STD: String = "std"
    const val CORE: String = "core"
    val stdlibCrates = listOf(
        // Roots
        StdLibInfo(STD, StdLibType.ROOT),
        StdLibInfo(CORE, StdLibType.ROOT),
        StdLibInfo("alloc", StdLibType.ROOT),
        StdLibInfo("proc_macro", StdLibType.ROOT),
        StdLibInfo("test", StdLibType.ROOT),
        // Feature gated
        StdLibInfo("panic_unwind", StdLibType.FEATURE_GATED),
        StdLibInfo("panic_abort", StdLibType.FEATURE_GATED),
        StdLibInfo("unwind", StdLibType.FEATURE_GATED),
        StdLibInfo("syntax", StdLibType.FEATURE_GATED),
        StdLibInfo("rustc", StdLibType.FEATURE_GATED),
        StdLibInfo("rustc_plugin", StdLibType.FEATURE_GATED),
        // Dependencies
        StdLibInfo("build_helper", StdLibType.DEPENDENCY, srcDir = "build_helper"),
        StdLibInfo("rustc_asan", StdLibType.DEPENDENCY),
        StdLibInfo("rustc_lsan", StdLibType.DEPENDENCY),
        StdLibInfo("rustc_msan", StdLibType.DEPENDENCY),
        StdLibInfo("rustc_tsan", StdLibType.DEPENDENCY)
    )
}

/**
 * Extracts Cargo based project's root-path (the one containing `Cargo.toml`)
 */
val Module.cargoProjectRoot: VirtualFile?
    get() = ModuleRootManager.getInstance(this).contentRoots.firstOrNull {
        it.findChild(RustToolchain.CARGO_TOML) != null
    }
