/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
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

    fun withStdlib(stdlib: StandardLibrary): CargoWorkspace
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

        val workspace: CargoWorkspace

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
        fun deserialize(manifestPath: Path, data: CargoWorkspaceData): CargoWorkspace
            = WorkspaceImpl.deserialize(manifestPath, data)
    }
}


private class WorkspaceImpl(
    override val manifestPath: Path,
    override val packages: Collection<PackageImpl>
) : CargoWorkspace {

    init {
        packages.forEach { it.initWorkspace(this) }
    }

    val targetByCrateRootUrl = packages.flatMap { it.targets }.associateBy { it.crateRootUrl }
    override fun findTargetByCrateRoot(root: VirtualFile): CargoWorkspace.Target? {
        val canonicalFile = root.canonicalFile ?: return null
        return targetByCrateRootUrl[canonicalFile.url]
    }

    override fun withStdlib(stdlib: StandardLibrary): CargoWorkspace {
        val nameToPkg = stdlib.crates.map { crate ->
            val pkg = PackageImpl(
                contentRootUrl = crate.packageRootUrl,
                name = crate.name,
                version = "",
                targets = listOf(CargoWorkspaceData.Target(crateRootUrl = crate.crateRootUrl, name = crate.name, kind = CargoWorkspace.TargetKind.LIB)),
                source = null,
                origin = PackageOrigin.STDLIB
            )
            (crate.name to pkg)
        }.toMap()

        // Bind dependencies and collect roots
        val roots = ArrayList<PackageImpl>()
        val featureGated = ArrayList<PackageImpl>()
        stdlib.crates.forEach { crate ->
            val slib = nameToPkg[crate.name] ?: error("Std lib ${crate.name} not found")
            val depPackages = crate.dependencies.mapNotNull { nameToPkg[it] }
            slib.dependencies.addAll(depPackages)
            when (crate.type) {
                StdLibType.ROOT -> roots.add(slib)
                StdLibType.FEATURE_GATED -> featureGated.add(slib)
                StdLibType.DEPENDENCY -> Unit
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
        fun deserialize(manifestPath: Path, data: CargoWorkspaceData): WorkspaceImpl {
            // Packages form mostly a DAG. "Why mostly?", you say.
            // Well, a dev-dependency `X` of package `P` can depend on the `P` itself.
            // This is ok, because cargo can compile `P` (without `X`, because dev-deps
            // are used only for tests), then `X`, and then `P`s tests. So we need to
            // handle cycles here.

            // Figure out packages origins:
            // - if a package is a workspace member it's WORKSPACE
            // - if a package is a direct dependency of a workspace member, it's DEPENDENCY
            // - otherwise, it's TRANSITIVE_DEPENDENCY
            val idToOrigin = HashMap<PackageId, PackageOrigin>(data.packages.size)
            for (pkg in data.packages) {
                if (pkg.isWorkspaceMember) {
                    idToOrigin[pkg.id] = PackageOrigin.WORKSPACE
                    for (dep in data.dependencies[pkg.id].orEmpty()) {
                        idToOrigin.merge(dep, PackageOrigin.DEPENDENCY, { o1, o2 -> PackageOrigin.min(o1, o2) })
                    }
                } else {
                    idToOrigin.putIfAbsent(pkg.id, PackageOrigin.TRANSITIVE_DEPENDENCY)
                }
            }

            val packages = data.packages.associate { pkg ->
                val origin = idToOrigin[pkg.id] ?: error("Origin is undefined for package ${pkg.name}")
                pkg.id to PackageImpl(
                    pkg.contentRootUrl,
                    pkg.name,
                    pkg.version,
                    pkg.targets,
                    pkg.source,
                    origin
                )
            }

            // Fill package dependencies
            packages.forEach { (id, pkg) ->
                val deps = data.dependencies[id].orEmpty()
                pkg.dependencies.addAll(deps.mapNotNull { packages[it] })
            }

            return WorkspaceImpl(manifestPath, packages.values.toList())
        }
    }
}


private class PackageImpl(
    // Note: In tests, we use in-memory file system,
    // so we can't use `Path` here.
    private val contentRootUrl: String,
    override val name: String,
    override val version: String,
    targets: Collection<CargoWorkspaceData.Target>,
    override val source: String?,
    override val origin: PackageOrigin
) : CargoWorkspace.Package {
    override val targets = targets.map { TargetImpl(this, crateRootUrl = it.crateRootUrl, name = it.name, kind = it.kind) }

    override val contentRoot: VirtualFile?
        get() = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl)

    override val rootDirectory: Path
        get() = Paths.get(VirtualFileManager.extractPath(contentRootUrl))

    override val dependencies: MutableList<PackageImpl> = ArrayList()

    private lateinit var myWorkspace: WorkspaceImpl
    fun initWorkspace(workspace: WorkspaceImpl) {
        myWorkspace = workspace
    }

    override val workspace: CargoWorkspace get() = myWorkspace


    override fun toString()
        = "Package(name='$name', contentRootUrl='$contentRootUrl')"
}


private class TargetImpl(
    override val pkg: PackageImpl,
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

    override fun toString(): String
        = "Target(name='$name', kind=$kind, crateRootUrl='$crateRootUrl')"
}
