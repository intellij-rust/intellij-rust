/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.cargo.project.workspace

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.cargoProjects
import org.rust.ide.icons.RsIcons
import org.rust.openapiext.checkReadAccessAllowed
import javax.swing.Icon

/**
 * IDEA side of Cargo package from crates.io
 */
class CargoLibrary(
    private val name: String,
    private val sourceRoots: Set<VirtualFile>,
    private val excludedRoots: Set<VirtualFile>,
    private val isStd: Boolean
) : SyntheticLibrary(), ItemPresentation {
    override fun getSourceRoots(): Collection<VirtualFile> = sourceRoots
    override fun getExcludedRoots(): Set<VirtualFile> = excludedRoots

    override fun equals(other: Any?): Boolean = other is CargoLibrary && other.sourceRoots == sourceRoots
    override fun hashCode(): Int = sourceRoots.hashCode()

    override fun getLocationString(): String? = null

    override fun getIcon(unused: Boolean): Icon? = if (isStd) RsIcons.RUST else CargoIcons.ICON

    override fun getPresentableText(): String? = name
}


class RsAdditionalLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<CargoLibrary> {
        checkReadAccessAllowed()
        return project.cargoProjects.allProjects
            .mapNotNull { it.workspace }
            .smartFlatMap { it.ideaLibraries }
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
        getAdditionalProjectLibraries(project).flatMap { it.sourceRoots }
}

private fun <U, V> Collection<U>.smartFlatMap(transform: (U) -> Collection<V>): Collection<V> =
    when (size) {
        0 -> emptyList()
        1 -> transform(first())
        else -> this.flatMap(transform)
    }

private val CargoWorkspace.ideaLibraries: Collection<CargoLibrary>
    get() = packages.filter { it.origin != PackageOrigin.WORKSPACE }
        .mapNotNull { pkg ->
            val root = pkg.contentRoot ?: return@mapNotNull null
            val isStd = pkg.origin == PackageOrigin.STDLIB
            val (sourceRoots, excludedRoots) = if (isStd) {
                setOf(root) to listOfNotNull(root.findChild("tests"), root.findChild("benches")).toSet()
            } else {
                val sourceRoots = mutableSetOf<VirtualFile>()
                val excludedRoots = mutableSetOf<VirtualFile>()
                for (target in pkg.targets) {
                    val crateRoot = target.crateRoot ?: continue
                    if (target.isLib) {
                        val crateRootDir = crateRoot.parent
                        val commonAncestor = VfsUtilCore.getCommonAncestor(root, crateRootDir)
                        when (commonAncestor) {
                            root -> sourceRoots += root
                            crateRootDir -> sourceRoots += crateRootDir
                            else -> {
                                sourceRoots += root
                                sourceRoots += crateRootDir
                            }
                        }
                    } else {
                        // TODO exclude full module hierarchy instead of crate roots only
                        excludedRoots += crateRoot
                    }
                }
                sourceRoots to excludedRoots
            }
            CargoLibrary(pkg.name, sourceRoots, excludedRoots, isStd)
        }

