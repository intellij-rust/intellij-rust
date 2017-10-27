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

    // These are paths and files names used by Cargo to infer targets without Cargo.toml
    // https://github.com/rust-lang/cargo/blob/2c2e07f5cfc9a5de10854654bc1e8abd02ae7b4f/src/cargo/util/toml.rs#L50-L56
    private val IMPLICIT_TARGET_FILES = listOf(
        "/build.rs", "/src/main.rs", "/src/lib.rs"
    )

    private val IMPLICIT_TARGET_DIRS = listOf(
        "/src/bin", "/examples", "/tests", "/benches"
    )

    override fun before(events: List<VFileEvent>) {
    }

    override fun after(events: List<VFileEvent>) {
        fun isInterestingEvent(event: VFileEvent): Boolean {
            if (event.isFile(CARGO_TOML) || event.isFile(CARGO_LOCK)) return true
            if (event is VFileContentChangeEvent || PathUtil.getFileExtension(event.path) != "rs") return false
            if (event is VFilePropertyChangeEvent && event.propertyName != VirtualFile.PROP_NAME) return false
            val parent = PathUtil.getParentPath(event.path)
            val grandParent = PathUtil.getParentPath(parent)

            if (IMPLICIT_TARGET_FILES.any { event.isFile(it) }) return true
            return IMPLICIT_TARGET_DIRS.any { parent.endsWith(it) || (event.isFile("main.rs") && grandParent.endsWith(it)) }
        }

        if (events.any(::isInterestingEvent)) {
            onCargoTomlChange()
        }
    }

    private fun VFileEvent.isFile(name: String): Boolean = path.endsWith(name) ||
        this is VFilePropertyChangeEvent && oldPath.endsWith(name)
}
