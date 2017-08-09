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
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Rust project model represented roughly in the same way as in Cargo itself.
 *
 * [CargoProjectWorkspaceService] is responsible for providing a [CargoWorkspace] for
 * an IDEA module.
 */
class CargoWorkspace private constructor(
    val manifestPath: Path?,
    val packages: Collection<Package>
) {
    class Package(
        private val contentRootUrl: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?,
        val origin: PackageOrigin
    ) {
        val normName = name.replace('-', '_')
        val dependencies: MutableList<Package> = ArrayList()
        val libTarget: Target? get() = targets.find { it.isLib }
        val contentRoot: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl)

        override fun toString() = "Package(contentRootUrl='$contentRootUrl', name='$name')"

        fun initTargets(): Package {
            targets.forEach { it.initPackage(this) }
            return this
        }

        fun findCrateByName(normName: String): Target? =
            if (this.normName == normName) libTarget else dependencies.findLibrary(normName)
    }

    class Target(
        /**
         * Absolute path to the crate root file
         */
        internal val crateRootUrl: String,
        val name: String,
        val kind: TargetKind
    ) {
        // target name must be a valid Rust identifier, so normalize it by mapping `-` to `_`
        // https://github.com/rust-lang/cargo/blob/ece4e963a3054cdd078a46449ef0270b88f74d45/src/cargo/core/manifest.rs#L299
        val normName = name.replace('-', '_')
        val isLib: Boolean get() = kind == TargetKind.LIB
        val isBin: Boolean get() = kind == TargetKind.BIN
        val isExample: Boolean get() = kind == TargetKind.EXAMPLE

        private val crateRootCache = AtomicReference<VirtualFile>()
        val crateRoot: VirtualFile? get() {
            val cached = crateRootCache.get()
            if (cached != null && cached.isValid) return cached
            val file = VirtualFileManager.getInstance().findFileByUrl(crateRootUrl)
            crateRootCache.set(file)
            return file
        }

        private lateinit var myPackage: Package
        fun initPackage(pkg: Package) {
            myPackage = pkg
        }

        val pkg: Package get() = myPackage

        override fun toString(): String
            = "Target(crateRootUrl='$crateRootUrl', name='$name', kind=$kind)"
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
    }

    private val targetByCrateRootUrl = packages.flatMap { it.targets }.associateBy { it.crateRootUrl }

    fun findCrateByNameApproximately(normName: String): Target? = packages.findLibrary(normName)

    /**
     * If the [file] is a crate root, returns the corresponding [Target]
     */
    fun findTargetForCrateRootFile(file: VirtualFile): Target? {
        val canonicalFile = file.canonicalFile ?: return null
        return targetByCrateRootUrl[canonicalFile.url]
    }
    fun isCrateRoot(file: VirtualFile): Boolean = findTargetForCrateRootFile(file) != null

    fun findPackage(name: String): Package? = packages.find { it.name == name }

    val hasStandardLibrary: Boolean get() = packages.any { it.origin == PackageOrigin.STDLIB }

    val contentRoot: Path? = manifestPath?.parent

    fun withStdlib(libs: List<StandardLibrary.StdCrate>): CargoWorkspace {
        val stdlib = libs.map { crate ->
            val pkg = Package(
                contentRootUrl = crate.packageRootUrl,
                name = crate.name,
                version = "",
                targets = listOf(Target(crate.crateRootUrl, name = crate.name, kind = TargetKind.LIB)),
                source = null,
                origin = PackageOrigin.STDLIB
            ).initTargets()
            (crate.name to pkg)
        }.toMap()

        // Bind dependencies and collect roots
        val roots = ArrayList<Package>()
        val featureGated = ArrayList<Package>()
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

        return CargoWorkspace(manifestPath, packages + roots)
    }

    companion object {
        fun deserialize(manifestPath: Path?, data: CleanCargoMetadata): CargoWorkspace {
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
                Package(
                    pkg.url,
                    pkg.name,
                    pkg.version,
                    pkg.targets.map { Target(it.url, it.name, it.kind) },
                    pkg.source,
                    origin
                ).initTargets()
            }.toList()

            // Fill package dependencies
            packages.forEachIndexed pkgs@ { index, pkg ->
                val depNode = data.dependencies.getOrNull(index) ?: return@pkgs
                pkg.dependencies.addAll(depNode.dependenciesIndexes.map { packages[it] })
            }

            return CargoWorkspace(manifestPath, packages)
        }
    }
}

private fun Collection<CargoWorkspace.Package>.findLibrary(normName: String): CargoWorkspace.Target? =
    filter { it.normName == normName }
        .mapNotNull { it.libTarget }
        .firstOrNull()
