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
    val crates: List<StdCrate>
) {

    data class StdCrate(
        val name: String,
        val type: StdLibType,
        val crateRootUrl: String,
        val packageRootUrl: String,
        val dependencies: Collection<String>
    ) {
        val id: PackageId get() = "(stdlib) $name"
    }

    companion object {
        fun fromPath(path: String): StandardLibrary? =
            LocalFileSystem.getInstance().findFileByPath(path)?.let { fromFile(it) }

        fun fromFile(sources: VirtualFile): StandardLibrary? {
            if (!sources.isDirectory) return null
            val srcDir = if (sources.name == "src") {
                sources
            } else {
                sources.findChild("src") ?: sources
            }

            val stdlib = AutoInjectedCrates.stdlibCrates.mapNotNull { libInfo ->
                val packageSrcDir = srcDir.findFileByRelativePath(libInfo.srcDir)?.canonicalFile
                val libFile = packageSrcDir?.findChild("lib.rs")
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
