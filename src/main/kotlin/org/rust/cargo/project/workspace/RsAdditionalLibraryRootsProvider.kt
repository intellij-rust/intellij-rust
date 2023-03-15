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
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.ide.icons.RsIcons
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

    override fun getIcon(unused: Boolean): Icon = icon

    override fun getPresentableText(): String = if (version != null) "$name $version" else name
}

class GeneratedCodeFakeLibrary(private val sourceRoots: Set<VirtualFile>) : SyntheticLibrary() {
    override fun equals(other: Any?): Boolean {
        return other is GeneratedCodeFakeLibrary && other.sourceRoots == sourceRoots
    }

    override fun hashCode(): Int = sourceRoots.hashCode()
    override fun getSourceRoots(): Collection<VirtualFile> = sourceRoots
    override fun isShowInExternalLibrariesNode(): Boolean = false

    companion object {
        fun create(cargoProject: CargoProject): GeneratedCodeFakeLibrary? {
            val generatedRoots = cargoProject.workspace?.packages.orEmpty().mapNotNullTo(HashSet()) { it.outDir }
            return if (generatedRoots.isEmpty()) null else GeneratedCodeFakeLibrary(generatedRoots)
        }
    }
}

class RsAdditionalLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> =
        project.cargoProjects.allProjects.smartFlatMap { it.ideaLibraries }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
        getAdditionalProjectLibraries(project).flatMap { it.sourceRoots }
}

private fun <U, V> Collection<U>.smartFlatMap(transform: (U) -> Collection<V>): Collection<V> =
    when (size) {
        0 -> emptyList()
        1 -> transform(first())
        else -> this.flatMap(transform)
    }

private val CargoProject.ideaLibraries: Collection<SyntheticLibrary>
    get() {
        val workspace = workspace ?: return emptyList()
        val stdlibPackages = mutableListOf<CargoWorkspace.Package>()
        val dependencyPackages = mutableListOf<CargoWorkspace.Package>()
        for (pkg in workspace.packages) {
            when (pkg.origin) {
                STDLIB, STDLIB_DEPENDENCY -> stdlibPackages += pkg
                DEPENDENCY -> dependencyPackages += pkg
                WORKSPACE -> Unit
            }.exhaustive
        }

        return buildList {
            makeStdlibLibrary(stdlibPackages, rustcInfo?.version)?.let(this::add)
            for (pkg in dependencyPackages) {
                pkg.toCargoLibrary()?.let(this::add)
            }
            GeneratedCodeFakeLibrary.create(this@ideaLibraries)?.let(::add)
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
        excludedRoots += listOfNotNull(
            root.findChild("tests"),
            root.findChild("benches"),
            root.findChild("examples"),
            root.findChild("ci"), // From `backtrace`
            root.findChild(".github"), // From `backtrace`
            root.findChild("libc-test") // From Rust 1.32.0 `liblibc`
        )
    }

    val version = rustcVersion?.semver?.parsedVersion
    return CargoLibrary("stdlib", sourceRoots, excludedRoots, RsIcons.RUST, version)
}

private fun CargoWorkspace.Package.toCargoLibrary(): CargoLibrary? {
    val root = contentRoot ?: return null
    val sourceRoots = mutableSetOf<VirtualFile>()
    val excludedRoots = mutableSetOf<VirtualFile>()
    for (target in targets) {
        val crateRoot = target.crateRoot ?: continue
        if (target.kind.isLib || target.kind.isCustomBuild) {
            val crateRootDir = crateRoot.parent
            when (VfsUtilCore.getCommonAncestor(root, crateRootDir)) {
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
