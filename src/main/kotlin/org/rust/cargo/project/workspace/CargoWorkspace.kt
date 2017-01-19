package org.rust.cargo.project.workspace

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.rust.cargo.toolchain.impl.CleanCargoMetadata
import org.rust.cargo.util.AutoInjectedCrates
import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * Rust project model represented roughly in the same way as in Cargo itself.
 *
 * [CargoProjectWorkspaceService] is responsible for providing a [CargoWorkspace] for
 * an IDEA module.
 */
class CargoWorkspace private constructor(
    val packages: Collection<Package>
) {

    class Package(
        val contentRootUrl: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?,
        val origin: PackageOrigin
    ) {
        val libTarget: Target? get() = targets.find { it.isLib }
        val contentRoot: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl)

        override fun toString() = "Package(contentRootUrl='$contentRootUrl', name='$name')"
    }

    class Target(
        /**
         * Absolute path to the crate root file
         */
        val crateRootUrl: String,
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

    fun findCrateByName(normName: String): Target? =
        packages
            .mapNotNull { it.libTarget }
            .find { it.normName == normName }

    /**
     * If the [file] is a crate root, returns the corresponding [Target]
     */
    fun findTargetForCrateRootFile(file: VirtualFile): Target? {
        val canonicalFile = file.canonicalFile ?: return null
        return targetByCrateRootUrl[canonicalFile.url]
    }

    fun findPackage(name: String): Package? = packages.find { it.name == name }

    fun isCrateRoot(file: VirtualFile): Boolean = findTargetForCrateRootFile(file) != null

    /**
     * Combines information about the project structure we got form cargo and information
     * about the standard library that is stored as an IDEA external library
     */
    fun withStdlib(lib: Library): CargoWorkspace {
        val roots: Map<String, VirtualFile> = lib.getFiles(OrderRootType.CLASSES).associateBy { it.name }
        val stdlibPackages = AutoInjectedCrates.stdlibCrateNames.mapNotNull { name ->
            roots["lib$name"]?.let { libDir ->
                val file = libDir.findFileByRelativePath("lib.rs") ?: return@let null
                name to file
            }
        }

        val stdlib = stdlibPackages.map {
            val (crateName, crateRoot) = it
            Package(
                contentRootUrl = crateRoot.parent.url,
                name = crateName,
                version = "",
                targets = listOf(Target(crateRoot.url, name = crateName, kind = TargetKind.LIB)),
                source = null,
                origin = PackageOrigin.STDLIB
            )
        }
        return CargoWorkspace(packages + stdlib)
    }

    val hasStandardLibrary: Boolean get() = packages.any { it.origin == PackageOrigin.STDLIB }

    companion object {
        fun deserialize(data: CleanCargoMetadata): CargoWorkspace {
            // Packages form mostly a DAG. "Why mostly?", you say.
            // Well, a dev-dependency `X` of package `P` can depend on the `P` itself.
            // This is ok, because cargo can compile `P` (without `X`, because dev-deps
            // are used only for tests), then `X`, and then `P`s tests. So we need to
            // handle cycles here.

            // Figure out packages origins:
            // - if a package is a workspace member, it's WORKSPACE
            // - if a package is a direct dependency of a workspace member, it's DEPENDENCY
            // - otherwise, it's TRANSITIVE_DEPENDENCY
            val nameToOrigin = HashMap<String, PackageOrigin>(data.packages.size)
            data.packages.forEachIndexed pkgs@ { index, pkg ->
                if (pkg.isWorkspaceMember) {
                    nameToOrigin[pkg.name] = PackageOrigin.WORKSPACE
                    val depNode = data.dependencies.getOrNull(index) ?: return@pkgs
                    depNode.dependenciesIndexes
                        .mapNotNull { data.packages.getOrNull(it) }
                        .forEach {
                            nameToOrigin.merge(it.name, PackageOrigin.DEPENDENCY, { o1, o2 -> PackageOrigin.min(o1, o2) })
                        }
                } else {
                    nameToOrigin.putIfAbsent(pkg.name, PackageOrigin.TRANSITIVE_DEPENDENCY)
                }
            }

            val packages = data.packages.map { pkg ->
                val origin = nameToOrigin[pkg.name] ?: error("Origin is undefined for package ${pkg.name}")
                Package(
                    pkg.url,
                    pkg.name,
                    pkg.version,
                    pkg.targets.map { Target(it.url, it.name, it.kind) },
                    pkg.source,
                    origin
                ).apply {
                    for (target in targets) {
                        target.initPackage(this)
                    }
                }
            }

            return CargoWorkspace(packages)
        }
    }
}

