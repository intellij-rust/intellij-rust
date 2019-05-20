/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.intellij.execution.ExecutionException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.StdLibType
import org.rust.openapiext.pathAsPath

class StandardLibrary private constructor(
    val crates: List<StdCrate>
) {

    data class StdCrate(
        val pkg: CargoWorkspaceData.Package,
        val type: StdLibType,
        val dependencies: Set<CargoWorkspaceData.Dependency>
    ) {
        val id: PackageId get() = pkg.id
    }

    companion object {

        private val LOG: Logger = Logger.getInstance(StandardLibrary::class.java)

        fun fromPath(project: Project, path: String): StandardLibrary? =
            LocalFileSystem.getInstance().findFileByPath(path)?.let { fromFile(project, it) }

        fun fromFile(project: Project, sources: VirtualFile): StandardLibrary? {
            if (!sources.isDirectory) return null
            val cargo = project.toolchain?.rawCargo() ?: return null
            val srcDir = if (sources.name == "src") {
                sources
            } else {
                sources.findChild("src") ?: sources
            }

            val stdlib = AutoInjectedCrates.stdlibCrates.mapNotNull { libInfo ->
                val rootDir = srcDir.findFileByRelativePath(libInfo.srcDir)?.canonicalFile ?: return@mapNotNull null
                val workspace = try {
                    cargo.projectWorkspaceData(project, rootDir.pathAsPath)
                } catch (e: ExecutionException) {
                    LOG.warn("Failed to get workspace data of ${libInfo.name}", e)
                    return@mapNotNull null
                }

                val pkg = workspace.packages.firstOrNull { it.origin == PackageOrigin.WORKSPACE } ?: return@mapNotNull null
                StdCrate(
                    pkg = pkg.copy(origin = PackageOrigin.STDLIB),
                    type = libInfo.type,
                    dependencies = workspace.dependencies[pkg.id].orEmpty()
                )
            }
            if (stdlib.isEmpty()) return null
            return StandardLibrary(stdlib)
        }
    }
}
