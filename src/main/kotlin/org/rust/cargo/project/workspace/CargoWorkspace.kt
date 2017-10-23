/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.rust.cargo.toolchain.impl.CleanCargoMetadata
import org.rust.cargo.util.StdLibType
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Rust project model represented roughly in the same way as in Cargo itself.
 *
 * [org.rust.cargo.project.model.CargoProjectsService] manages workspaces.
 */
interface CargoWorkspace {
    val manifestPath: Path
    val contentRoot: Path get() = manifestPath.parent

    val packages: Collection<Package>
    fun findPackage(name: String): Package? = packages.find { it.name == name }

    fun findTargetByCrateRoot(root: VirtualFile): Target?
    fun isCrateRoot(root: VirtualFile) = findTargetByCrateRoot(root) != null

    fun withStdlib(libs: List<StandardLibrary.StdCrate>): CargoWorkspace
    val hasStandardLibrary: Boolean get() = packages.any { it.origin == PackageOrigin.STDLIB }

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

        fun findDependency(normName: String): Target? =
            if (this.normName == normName) libTarget else dependencies.find { it.normName == normName }?.libTarget
    }

    interface Target {
        val name: String
        // target name must be a valid Rust identifier, so normalize it by mapping `-` to `_`
        // https://github.com/rust-lang/cargo/blob/ece4e963a3054cdd078a46449ef0270b88f74d45/src/cargo/core/manifest.rs#L299
        val normName: String get() = name.replace('-', '_')

        val kind: TargetKind
        val isLib: Boolean get() = kind == TargetKind.LIB
        val isBin: Boolean get() = kind == TargetKind.BIN
        val isExample: Boolean get() = kind == TargetKind.EXAMPLE

        val crateRoot: VirtualFile?

        val pkg: Package
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
    }

    companion object {
        fun deserialize(manifestPath: Path, data: CleanCargoMetadata): CargoWorkspace
            = WorkspaceImpl.deserialize(manifestPath, data)
    }
}


private class WorkspaceImpl(
    override val manifestPath: Path,
    override val packages: Collection<PackageImpl>
) : CargoWorkspace {

    val targetByCrateRootUrl = packages.flatMap { it.targets }.associateBy { it.crateRootUrl }
    override fun findTargetByCrateRoot(root: VirtualFile): CargoWorkspace.Target? {
        val canonicalFile = root.canonicalFile ?: return null
        return targetByCrateRootUrl[canonicalFile.url]
    }

    override fun withStdlib(libs: List<StandardLibrary.StdCrate>): CargoWorkspace {
        val stdlib = libs.map { crate ->
            val pkg = PackageImpl(
                contentRootUrl = crate.packageRootUrl,
                name = crate.name,
                version = "",
                targets = listOf(TargetImpl(crate.crateRootUrl, name = crate.name, kind = CargoWorkspace.TargetKind.LIB)),
                source = null,
                origin = PackageOrigin.STDLIB
            ).initTargets()
            (crate.name to pkg)
        }.toMap()

        // Bind dependencies and collect roots
        val roots = ArrayList<PackageImpl>()
        val featureGated = ArrayList<PackageImpl>()
        libs.forEach { lib ->
            val slib = stdlib[lib.name] ?: error("Std lib ${lib.name} not found")
            val depPackages = lib.dependencies.mapNotNull { stdlib[it] }
            slib.dependencies.addAll(depPackages)
            if (lib.type == StdLibType.ROOT) {
                roots.add(slib)
            } else if (lib.type == StdLibType.FEATURE_GATED) {
                featureGated.add(slib)
            }
        }

        roots.forEach { it.dependencies.addAll(roots) }
        packages.forEach { pkg ->
            // Only add feature gated crates which names don't conflict with own dependencies
            val packageFeatureGated = featureGated.filter { o -> pkg.dependencies.none { it.name == o.name } }
            pkg.dependencies.addAll(roots + packageFeatureGated)
        }

        return WorkspaceImpl(manifestPath, packages + roots)
    }

    override fun toString(): String {
        val pkgs = packages.joinToString(separator = "") { "    $it,\n" }
        return "Workspace(packages=[\n$pkgs])"
    }

    companion object {
        fun deserialize(manifestPath: Path, data: CleanCargoMetadata): WorkspaceImpl {
            // Packages form mostly a DAG. "Why mostly?", you say.
            // Well, a dev-dependency `X` of package `P` can depend on the `P` itself.
            // This is ok, because cargo can compile `P` (without `X`, because dev-deps
            // are used only for tests), then `X`, and then `P`s tests. So we need to
            // handle cycles here.

            // Figure out packages origins:
            // - if a package is a workspace member, or if it resides inside a workspace member directory, it's WORKSPACE
            // - if a package is a direct dependency of a workspace member, it's DEPENDENCY
            // - otherwise, it's TRANSITIVE_DEPENDENCY
            val idToOrigin = HashMap<String, PackageOrigin>(data.packages.size)
            val workspacePaths = data.packages
                .filter { it.isWorkspaceMember }
                .map { it.manifestPath.substringBeforeLast("Cargo.toml", "") }
                .filter(String::isNotEmpty)
                .toList()
            data.packages.forEachIndexed pkgs@ { index, pkg ->
                if (pkg.isWorkspaceMember || workspacePaths.any { pkg.manifestPath.startsWith(it) }) {
                    idToOrigin[pkg.id] = PackageOrigin.WORKSPACE
                    val depNode = data.dependencies.getOrNull(index) ?: return@pkgs
                    depNode.dependenciesIndexes
                        .mapNotNull { data.packages.getOrNull(it) }
                        .forEach {
                            idToOrigin.merge(it.id, PackageOrigin.DEPENDENCY, { o1, o2 -> PackageOrigin.min(o1, o2) })
                        }
                } else {
                    idToOrigin.putIfAbsent(pkg.id, PackageOrigin.TRANSITIVE_DEPENDENCY)
                }
            }

            val packages = data.packages.map { pkg ->
                val origin = idToOrigin[pkg.id] ?: error("Origin is undefined for package ${pkg.name}")
                PackageImpl(
                    pkg.url,
                    pkg.name,
                    pkg.version,
                    pkg.targets.map { TargetImpl(it.url, it.name, it.kind) },
                    pkg.source,
                    origin
                ).initTargets()
            }.toList()

            // Fill package dependencies
            packages.forEachIndexed pkgs@ { index, pkg ->
                val depNode = data.dependencies.getOrNull(index) ?: return@pkgs
                pkg.dependencies.addAll(depNode.dependenciesIndexes.map { packages[it] })
            }

            return WorkspaceImpl(manifestPath, packages)
        }
    }
}


private class PackageImpl(
    // Note: In tests, we use in-memory file system,
    // so we can't use `Path` here.
    private val contentRootUrl: String,
    override val name: String,
    override val version: String,
    override val targets: Collection<TargetImpl>,
    override val source: String?,
    override val origin: PackageOrigin
) : CargoWorkspace.Package {

    override val contentRoot: VirtualFile?
        get() = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl)

    override val rootDirectory: Path
        get() = Paths.get(VirtualFileManager.extractPath(contentRootUrl))

    override val dependencies: MutableList<PackageImpl> = ArrayList()

    fun initTargets(): PackageImpl {
        targets.forEach { it.initPackage(this) }
        return this
    }

    override fun toString()
        = "Package(name='$name', contentRootUrl='$contentRootUrl')"
}


private class TargetImpl(
    val crateRootUrl: String,
    override val name: String,
    override val kind: CargoWorkspace.TargetKind
) : CargoWorkspace.Target {

    private val crateRootCache = AtomicReference<VirtualFile>()
    override val crateRoot: VirtualFile?
        get() {
            val cached = crateRootCache.get()
            if (cached != null && cached.isValid) return cached
            val file = VirtualFileManager.getInstance().findFileByUrl(crateRootUrl)
            crateRootCache.set(file)
            return file
        }

    private lateinit var myPackage: PackageImpl
    fun initPackage(pkg: PackageImpl) {
        myPackage = pkg
    }

    override val pkg: CargoWorkspace.Package get() = myPackage

    override fun toString(): String
        = "Target(name='$name', kind=$kind, crateRootUrl='$crateRootUrl')"
}
