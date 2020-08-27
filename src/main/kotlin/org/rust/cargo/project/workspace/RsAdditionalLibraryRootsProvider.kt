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
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin.*
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.toolchain.RustcVersion
import org.rust.ide.icons.RsIcons
import org.rust.openapiext.checkReadAccessAllowed
import org.rust.stdext.buildList
import org.rust.stdext.exhaustive
import javax.swing.Icon

/**
 * IDEA side of Cargo package from crates.io
 */
class CargoLibrary(
    private val name: String,
    private val sourceRoots: Set<VirtualFile>,
    private val excludedRoots: Set<VirtualFile>,
    private val icon: Icon,
    private val version: String?
) : SyntheticLibrary(), ItemPresentation {
    override fun getSourceRoots(): Collection<VirtualFile> = sourceRoots
    override fun getExcludedRoots(): Set<VirtualFile> = excludedRoots

    override fun equals(other: Any?): Boolean = other is CargoLibrary && other.sourceRoots == sourceRoots
    override fun hashCode(): Int = sourceRoots.hashCode()

    override fun getLocationString(): String? = null

    override fun getIcon(unused: Boolean): Icon? = icon

    override fun getPresentableText(): String? = if (version != null) "$name $version" else name
}


class RsAdditionalLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<CargoLibrary> {
        checkReadAccessAllowed()
        return project.cargoProjects.allProjects
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

private val CargoProject.ideaLibraries: Collection<CargoLibrary>
    get() {
        val workspace = workspace ?: return emptyList()
        val stdlibPackages = mutableListOf<CargoWorkspace.Package>()
        val dependencyPackages = mutableListOf<CargoWorkspace.Package>()
        for (pkg in workspace.packages) {
            when (pkg.origin) {
                STDLIB -> stdlibPackages += pkg
                DEPENDENCY -> dependencyPackages += pkg
                WORKSPACE -> Unit
            }.exhaustive
        }

        return buildList {
            makeStdlibLibrary(stdlibPackages, rustcInfo?.version)?.let(this::add)
            for (pkg in dependencyPackages) {
                pkg.toCargoLibrary()?.let(this::add)
            }
        }
    }

private fun makeStdlibLibrary(packages: List<CargoWorkspace.Package>, rustcVersion: RustcVersion?): CargoLibrary? {
    if (packages.isEmpty()) return null
    val sourceRoots = mutableSetOf<VirtualFile>()
    val excludedRoots = mutableSetOf<VirtualFile>()
    for (pkg in packages) {
        val root = pkg.contentRoot ?: continue
        sourceRoots += root
        sourceRoots += pkg.additionalRoots()
    }

    for (root in sourceRoots) {
        excludedRoots += listOfNotNull(root.findChild("tests"), root.findChild("benches"))
    }

    val version = rustcVersion?.stdlibVersion()
    return CargoLibrary("stdlib", sourceRoots, excludedRoots, RsIcons.RUST, version)
}

private fun RustcVersion.stdlibVersion(): String = buildString {
    append(semver)
    if (channel > RustChannel.STABLE) {
        channel.channel?.let { append("-$it") }
    }
}

private fun CargoWorkspace.Package.toCargoLibrary(): CargoLibrary? {
    val root = contentRoot ?: return null
    val sourceRoots = mutableSetOf<VirtualFile>()
    val excludedRoots = mutableSetOf<VirtualFile>()
    for (target in targets) {
        val crateRoot = target.crateRoot ?: continue
        if (target.kind.isLib) {
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
    return CargoLibrary(name, sourceRoots, excludedRoots, CargoIcons.ICON, version)
}
