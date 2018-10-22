/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.util.StdLibType
import org.rust.openapiext.CachedVirtualFile
import org.rust.stdext.applyWithSymlink
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Rust project model represented roughly in the same way as in Cargo itself.
 *
 * [org.rust.cargo.project.model.CargoProjectsService] manages workspaces.
 */
interface CargoWorkspace {
    val manifestPath: Path
    val contentRoot: Path get() = manifestPath.parent

    val workspaceRootPath: Path?

    val packages: Collection<Package>
    fun findPackage(name: String): Package? = packages.find { it.name == name }

    fun findTargetByCrateRoot(root: VirtualFile): Target?
    fun isCrateRoot(root: VirtualFile) = findTargetByCrateRoot(root) != null

    fun withStdlib(stdlib: StandardLibrary): CargoWorkspace
    val hasStandardLibrary: Boolean get() = packages.any { it.origin == PackageOrigin.STDLIB }

    @TestOnly
    fun withEdition(edition: Edition): CargoWorkspace

    interface Package {
        val contentRoot: VirtualFile?
        val rootDirectory: Path

        val name: String
        val normName: String get() = name.replace('-', '_')

        val version: String

        val source: String?
        val origin: PackageOrigin

        val targets: Collection<Target>
        val libTarget: Target? get() = targets.find { it.isLib }

        val dependencies: Collection<Package>

        val workspace: CargoWorkspace

        val edition: Edition

        fun findDependency(normName: String): Target? =
            if (this.normName == normName) libTarget else dependencies.find { it.normName == normName }?.libTarget
    }

    interface Target {
        val name: String
        // target name must be a valid Rust identifier, so normalize it by mapping `-` to `_`
        // https://github.com/rust-lang/cargo/blob/ece4e963a3054cdd078a46449ef0270b88f74d45/src/cargo/core/manifest.rs#L299
        val normName: String get() = name.replace('-', '_')

        val kind: TargetKind
        val crateTypes: List<CrateType>

        val isLib: Boolean get() = kind == TargetKind.LIB
        val isBin: Boolean get() = kind == TargetKind.BIN
        val isExample: Boolean get() = kind == TargetKind.EXAMPLE

        val crateRoot: VirtualFile?

        val pkg: Package

        val edition: Edition
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
    }

    /**
     * Represents possible variants of generated artifact binary
     * corresponded to `--crate-type` compiler attribute
     *
     * See [linkage](https://doc.rust-lang.org/reference/linkage.html)
     */
    enum class CrateType {
        BIN, LIB, DYLIB, STATICLIB, CDYLIB, RLIB, PROC_MACRO, UNKNOWN
    }

    enum class Edition {
        EDITION_2015, EDITION_2018
    }

    companion object {
        fun deserialize(manifestPath: Path, data: CargoWorkspaceData): CargoWorkspace =
            WorkspaceImpl.deserialize(manifestPath, data)
    }
}


private class WorkspaceImpl(
    override val manifestPath: Path,
    override val workspaceRootPath: Path?,
    packagesData: Collection<CargoWorkspaceData.Package>
) : CargoWorkspace {
    override val packages: List<PackageImpl> = packagesData.map { pkg ->
        PackageImpl(
            this,
            pkg.id,
            pkg.contentRootUrl,
            pkg.name,
            pkg.version,
            pkg.targets,
            pkg.source,
            pkg.origin,
            pkg.edition
        )
    }

    val targetByCrateRootUrl = packages.flatMap { it.targets }.associateBy { it.crateRootUrl }

    override fun findTargetByCrateRoot(root: VirtualFile): CargoWorkspace.Target? =
        root.applyWithSymlink { targetByCrateRootUrl[it.url] }

    override fun withStdlib(stdlib: StandardLibrary): CargoWorkspace {
        // This is a bit trickier than it seems required.
        // The problem is that workspace packages and targets have backlinks
        // so we have to rebuild the whole workspace from scratch instead of
        // *just* adding in the stdlib.

        val stdAll = stdlib.crates.map { it.id }.toSet()
        val stdGated = stdlib.crates.filter { it.type == StdLibType.FEATURE_GATED }.map { it.id }.toSet()
        val stdRoots = stdlib.crates.filter { it.type == StdLibType.ROOT }.map { it.id }.toSet()

        val result = WorkspaceImpl(
            manifestPath,
            workspaceRootPath,
            packages.map { it.asPackageData() } +
                stdlib.crates.map { it.asPackageData }
        )

        run {
            val oldIdToPackage = packages.associateBy { it.id }
            val newIdToPackage = result.packages.associateBy { it.id }
            val stdlibPackages = result.packages.filter { it.origin == PackageOrigin.STDLIB }
            newIdToPackage.forEach { (id, pkg) ->
                if (id !in stdAll) {
                    pkg.dependencies.addAll(oldIdToPackage[id]?.dependencies.orEmpty().mapNotNull { newIdToPackage[it.id] })
                    pkg.dependencies.addAll(stdlibPackages.filter { it.id in stdRoots })
                    val explicitDeps = pkg.dependencies.map { it.id }.toSet()
                    pkg.dependencies.addAll(stdlibPackages.filter { it.id in stdGated && it.id !in explicitDeps })
                } else {
                    pkg.dependencies.addAll(stdlibPackages)
                }
            }
        }

        return result
    }

    @TestOnly
    override fun withEdition(edition: CargoWorkspace.Edition): CargoWorkspace {
        val result = WorkspaceImpl(
            manifestPath,
            workspaceRootPath,
            packages.map { pkg ->
                // Currently, stdlib doesn't use 2018 edition
                val packageEdition = if (pkg.origin == PackageOrigin.STDLIB) pkg.edition else edition
                pkg.asPackageData(packageEdition)
            }
        )

        val oldIdToPackage = packages.associateBy { it.id }
        val newIdToPackage = result.packages.associateBy { it.id }
        newIdToPackage.forEach { (id, pkg) ->
            pkg.dependencies.addAll(oldIdToPackage[id]?.dependencies.orEmpty().mapNotNull { newIdToPackage[it.id] })
        }

        return result
    }

    override fun toString(): String {
        val pkgs = packages.joinToString(separator = "") { "    $it,\n" }
        return "Workspace(packages=[\n$pkgs])"
    }

    companion object {
        fun deserialize(manifestPath: Path, data: CargoWorkspaceData): WorkspaceImpl {
            // Packages form mostly a DAG. "Why mostly?", you say.
            // Well, a dev-dependency `X` of package `P` can depend on the `P` itself.
            // This is ok, because cargo can compile `P` (without `X`, because dev-deps
            // are used only for tests), then `X`, and then `P`s tests. So we need to
            // handle cycles here.

            val workspaceRootPath = data.workspaceRoot?.let { Paths.get(it) }
            val result = WorkspaceImpl(manifestPath, workspaceRootPath, data.packages)
            // Fill package dependencies
            run {
                val idToPackage = result.packages.associateBy { it.id }
                idToPackage.forEach { (id, pkg) ->
                    val deps = data.dependencies[id].orEmpty()
                    pkg.dependencies.addAll(deps.mapNotNull { idToPackage[it] })
                }
            }

            // Figure out packages origins:
            // - if a package is a workspace member it's WORKSPACE (handled in constructor)
            // - if a package is a direct dependency of a workspace member, it's DEPENDENCY
            // - otherwise, it's TRANSITIVE_DEPENDENCY (handled in constructor as well)
            result.packages.filter { it.origin == PackageOrigin.WORKSPACE }
                .flatMap { it.dependencies }
                .forEach { it.origin = PackageOrigin.min(it.origin, PackageOrigin.DEPENDENCY) }

            return result
        }
    }
}


private class PackageImpl(
    override val workspace: WorkspaceImpl,
    val id: PackageId,
    // Note: In tests, we use in-memory file system,
    // so we can't use `Path` here.
    val contentRootUrl: String,
    override val name: String,
    override val version: String,
    targetsData: Collection<CargoWorkspaceData.Target>,
    override val source: String?,
    override var origin: PackageOrigin,
    override val edition: CargoWorkspace.Edition
) : CargoWorkspace.Package {
    override val targets = targetsData.map {
        TargetImpl(
            this,
            crateRootUrl = it.crateRootUrl,
            name = it.name,
            kind = it.kind,
            crateTypes = it.crateTypes,
            edition = it.edition
        )
    }

    override val contentRoot: VirtualFile? by CachedVirtualFile(contentRootUrl)

    override val rootDirectory: Path
        get() = Paths.get(VirtualFileManager.extractPath(contentRootUrl))

    override val dependencies: MutableList<PackageImpl> = ArrayList()

    override fun toString()
        = "Package(name='$name', contentRootUrl='$contentRootUrl')"
}


private class TargetImpl(
    override val pkg: PackageImpl,
    val crateRootUrl: String,
    override val name: String,
    override val kind: CargoWorkspace.TargetKind,
    override val crateTypes: List<CargoWorkspace.CrateType>,
    override val edition: CargoWorkspace.Edition
) : CargoWorkspace.Target {

    override val crateRoot: VirtualFile? by CachedVirtualFile(crateRootUrl)

    override fun toString(): String
        = "Target(name='$name', kind=$kind, crateRootUrl='$crateRootUrl')"
}


private fun PackageImpl.asPackageData(edition: CargoWorkspace.Edition? = null): CargoWorkspaceData.Package =
    CargoWorkspaceData.Package(
        id = id,
        contentRootUrl = contentRootUrl,
        name = name,
        version = version,
        targets = targets.map {
            CargoWorkspaceData.Target(
                crateRootUrl = it.crateRootUrl,
                name = it.name,
                kind = it.kind,
                crateTypes = it.crateTypes,
                edition = edition ?: it.edition
            )
        },
        source = source,
        origin = origin,
        edition = edition ?: this.edition
    )

private val StandardLibrary.StdCrate.asPackageData
    get() =
        CargoWorkspaceData.Package(
            id = id,
            contentRootUrl = packageRootUrl,
            name = name,
            version = "",
            targets = listOf(CargoWorkspaceData.Target(
                crateRootUrl = crateRootUrl,
                name = name,
                kind = CargoWorkspace.TargetKind.LIB,
                crateTypes = listOf(CargoWorkspace.CrateType.LIB),
                edition = CargoWorkspace.Edition.EDITION_2015
            )),
            source = null,
            origin = PackageOrigin.STDLIB,
            edition = CargoWorkspace.Edition.EDITION_2015
        )
