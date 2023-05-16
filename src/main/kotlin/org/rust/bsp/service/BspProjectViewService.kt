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

    data class BspProjectView(
        val targets: MutableSet<String>
    )

    private var pojo = readPojo()

    private fun mapPackagesToPojo(rustPackages: List<CargoWorkspaceData.Package>): BspProjectView {
        val result = mutableSetOf<String>()
        rustPackages.forEach { pkg ->
            pkg.allTargets.forEach {
                result.add(pkg.name + ':' + it.name)
            }
        }
        return BspProjectView(result)
    }

    private fun getViewPath(): String? = project.basePath?.let { Path.of(it, filename).toString() }

    fun getActiveTargets() = pojo.targets.toList()
    fun updateTargets(
        rustPackages: List<CargoWorkspaceData.Package>
    ): List<String> {
        pojo = mapPackagesToPojo(rustPackages)
        return pojo.targets.toList()
    }

    fun generateTargetsFile() =
        getViewPath()?.let {
            FileWriter(it).use {
                gson.toJson(pojo, it)
            }
        }


    private fun readPojo(): BspProjectView {
        val pojoFile = getViewPath()?.toVirtualFile() ?: return BspProjectView(mutableSetOf())
        return try {
            gson.fromJson(VfsUtil.loadText(pojoFile), BspProjectView::class.java)
        } catch (e: Exception) {
            BspProjectView(mutableSetOf())
        }
    }

    fun filterIncludedPackages(
        rustTargets: List<BuildTargetIdentifier>
    ): List<BuildTargetIdentifier> {
        if (pojo.targets.isEmpty()) return rustTargets
        return rustTargets.filter { it.uri in pojo.targets }
    }

    fun includePackage(
        target: BuildTargetIdentifier
    ) {
        pojo.targets.add(target.uri)
        FileWriter(getViewPath()).use {
            gson.toJson(pojo, it)
        }
    }

    fun excludePackage(
        target: BuildTargetIdentifier
    ) {
        pojo.targets.remove(target.uri)
        FileWriter(getViewPath()).use {
            gson.toJson(pojo, it)
        }
    }
}

private fun String.toVirtualFile(): VirtualFile? =
    VirtualFileManager.getInstance().findFileByNioPath(Path(this))
