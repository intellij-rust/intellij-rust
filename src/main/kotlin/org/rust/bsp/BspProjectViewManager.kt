/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.FileWriter
import java.nio.file.Path
import org.rust.cargo.project.workspace.CargoWorkspaceData
import kotlin.io.path.Path

class BspProjectViewManager(
    private val baseDirectory: String,
    private val filename: String = "rust-targets.json"
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class BspPackageView(
        val name: String,
        val targets: List<String>
    )

    data class BspProjectView(
        val packages: List<BspPackageView>
    )

    private fun mapPackagesToPojo(rustPackages: List<CargoWorkspaceData.Package>): BspProjectView {
        return BspProjectView(rustPackages.map { pkg ->
            BspPackageView(
                pkg.name,
                pkg.allTargets.map {
                    it.name
                }
            )
        })
    }

    private fun getViewPath(): String = Path.of(baseDirectory, filename).toString()

    fun generateTargetsFile(
        rustPackages: List<CargoWorkspaceData.Package>
    ) {
        val pojo = mapPackagesToPojo(rustPackages)
        FileWriter(getViewPath()).use {
            gson.toJson(pojo, it)
        }
    }

    private fun readPojo(): BspProjectView? {
        val pojoFile = getViewPath().toVirtualFile() ?: return null
        return try {
            gson.fromJson(VfsUtil.loadText(pojoFile), BspProjectView::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun filterIncludedPackages(
        rustTargets: List<BuildTargetIdentifier>
    ): List<BuildTargetIdentifier> {
        val projectView = readPojo() ?: return rustTargets
        val bspTargetIds = projectView.packages.flatMap { pkg ->
            pkg.targets.map {
                "${pkg.name}:${it}"
            }
        }
        return rustTargets.filter { it.uri in bspTargetIds }
    }
}

private fun String.toVirtualFile(): VirtualFile? =
    VirtualFileManager.getInstance().findFileByNioPath(Path(this))
