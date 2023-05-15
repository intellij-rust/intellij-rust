/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.bsp.service

import ch.epfl.scala.bsp4j.BuildTargetIdentifier
import com.google.gson.GsonBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.rust.cargo.project.workspace.CargoWorkspaceData
import java.io.FileWriter
import java.nio.file.Path
import kotlin.io.path.Path

class BspProjectViewService(val project: Project) {
    private val filename: String = "rust-targets.json"
    private val gson = GsonBuilder().setPrettyPrinting().create()

    data class BspPackageView(
        val name: String,
        val targets: MutableList<String>
    )

    data class BspProjectView(
        val packages: MutableList<BspPackageView>
    )

    private var pojo = readPojo()

    private fun mapPackagesToPojo(rustPackages: List<CargoWorkspaceData.Package>): BspProjectView {
        return BspProjectView(rustPackages.map { pkg ->
            BspPackageView(
                pkg.name,
                pkg.allTargets.map {
                    it.name
                }.toMutableList()
            )
        }.toMutableList())
    }

    private fun getViewPath(): String? = project.basePath?.let { Path.of(it, filename).toString() }

    fun updateTargets(
        rustPackages: List<CargoWorkspaceData.Package>
    ): List<String> {
        pojo = mapPackagesToPojo(rustPackages)
        return pojo.packages.flatMap { pkg ->
            pkg.targets.map {
                "${pkg.name}:${it}"
            }
        }
    }

    fun generateTargetsFile() {
        getViewPath()?.let {
            FileWriter(it).use {
                gson.toJson(pojo, it)
            }
        }
    }

    private fun readPojo(): BspProjectView {
        val pojoFile = getViewPath()?.toVirtualFile() ?: return BspProjectView(mutableListOf())
        return try {
            gson.fromJson(VfsUtil.loadText(pojoFile), BspProjectView::class.java)
        } catch (_: Exception) {
            BspProjectView(mutableListOf())
        }
    }

    fun filterIncludedPackages(
        rustTargets: List<BuildTargetIdentifier>
    ): List<BuildTargetIdentifier> {
        val projectView = pojo
        if (pojo.packages.isEmpty()) return rustTargets
        val bspTargetIds = projectView.packages.flatMap { pkg ->
            pkg.targets.map {
                "${pkg.name}:${it}"
            }
        }
        return rustTargets.filter { it.uri in bspTargetIds }
    }

    fun includePackage(
        target: BuildTargetIdentifier
    ) {
        val (packageName, targetName) = target.uri.split(":", limit = 2)
        pojo.packages.add(pojo.packages.size, BspPackageView(packageName, listOf(targetName).toMutableList()))
        FileWriter(getViewPath()).use {
            gson.toJson(pojo, it)
        }
    }

    fun excludePackage(
        target: BuildTargetIdentifier
    ) {
        val (packageName, targetName) = target.uri.split(":", limit = 2)
        pojo.packages.forEach { pkg -> if (pkg.name == packageName) pkg.targets.removeIf { it == targetName } }
        pojo.packages.removeIf { it.targets.isEmpty() }
        FileWriter(getViewPath()).use {
            gson.toJson(pojo, it)
        }
    }
}

private fun String.toVirtualFile(): VirtualFile? =
    VirtualFileManager.getInstance().findFileByNioPath(Path(this))
