/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.StdLibType

class StandardLibrary private constructor(
    val crates: List<StdCrate>,
    val isPartOfCargoProject: Boolean = false
) {

    data class StdCrate(
        val name: String,
        val type: StdLibType,
        val crateRootUrl: String,
        val packageRootUrl: String,
        val dependencies: Collection<String>,
        val id: PackageId = "(stdlib) $name"
    )

    fun asPartOfCargoProject(): StandardLibrary = StandardLibrary(crates, true)

    companion object {
        private val SRC_ROOTS: List<String> = listOf("library", "src")
        private val LIB_PATHS: List<String> = listOf("src/lib.rs", "lib.rs")

        fun fromPath(path: String): StandardLibrary? =
            LocalFileSystem.getInstance().findFileByPath(path)?.let { fromFile(it) }

        fun fromFile(sources: VirtualFile): StandardLibrary? {
            if (!sources.isDirectory) return null

            fun VirtualFile.findFirstFileByRelativePaths(paths: List<String>): VirtualFile? {
                for (path in paths) {
                    val file = findFileByRelativePath(path)
                    if (file != null) return file
                }
                return null
            }

            val srcDir = if (sources.name in SRC_ROOTS) {
                sources
            } else {
                sources.findFirstFileByRelativePaths(SRC_ROOTS) ?: sources
            }

            val stdlib = AutoInjectedCrates.stdlibCrates.mapNotNull { libInfo ->
                val packageSrcPaths = listOf(libInfo.name, "lib${libInfo.name}")
                val packageSrcDir = srcDir.findFirstFileByRelativePaths(packageSrcPaths)?.canonicalFile
                val libFile = packageSrcDir?.findFirstFileByRelativePaths(LIB_PATHS)
                if (packageSrcDir != null && libFile != null)
                    StdCrate(libInfo.name, libInfo.type, libFile.url, packageSrcDir.url, libInfo.dependencies)
                else
                    null
            }
            if (stdlib.isEmpty()) return null
            return StandardLibrary(stdlib)
        }
    }
}
