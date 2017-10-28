/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.PathUtil
import org.rust.cargo.toolchain.RustToolchain.Companion.CARGO_LOCK
import org.rust.cargo.toolchain.RustToolchain.Companion.CARGO_TOML

/**
 * File changes listener, detecting changes inside the `Cargo.toml` files
 * and creation of `*.rs` files acting as automatic crate root.
 */
class CargoTomlWatcher(
    private val onCargoTomlChange: () -> Unit
) : BulkFileListener {
    override fun before(events: List<VFileEvent>) = Unit

    override fun after(events: List<VFileEvent>) {
        if (events.any(::isInterestingEvent)) onCargoTomlChange()
    }
}

// These are paths and files names used by Cargo to infer targets without Cargo.toml
// https://github.com/rust-lang/cargo/blob/2c2e07f5cfc9a5de10854654bc1e8abd02ae7b4f/src/cargo/util/toml.rs#L50-L56
private val IMPLICIT_TARGET_FILES = listOf(
    "/build.rs", "/src/main.rs", "/src/lib.rs"
)

private val IMPLICIT_TARGET_DIRS = listOf(
    "/src/bin", "/examples", "/tests", "/benches"
)

private fun isInterestingEvent(event: VFileEvent): Boolean = when {
    event.pathEndsWith(CARGO_TOML) || event.pathEndsWith(CARGO_LOCK) -> true
    event is VFileContentChangeEvent -> false
    !event.pathEndsWith(".rs") -> false
    event is VFilePropertyChangeEvent && event.propertyName != VirtualFile.PROP_NAME -> false
    IMPLICIT_TARGET_FILES.any { event.pathEndsWith(it) } -> true
    else -> {
        val parent = PathUtil.getParentPath(event.path)
        val grandParent = PathUtil.getParentPath(parent)
        IMPLICIT_TARGET_DIRS.any { parent.endsWith(it) || (event.pathEndsWith("main.rs") && grandParent.endsWith(it)) }
    }
}

private fun VFileEvent.pathEndsWith(suffix: String): Boolean = path.endsWith(suffix) ||
    this is VFilePropertyChangeEvent && oldPath.endsWith(suffix)
