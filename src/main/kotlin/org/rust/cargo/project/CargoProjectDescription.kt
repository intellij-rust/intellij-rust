package org.rust.cargo.project

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
 * [org.rust.cargo.project.workspace.CargoProjectWorkspace] is responsible for providing a [CargoProjectDescription] for
 * an IDEA module.
 */
class CargoProjectDescription private constructor(
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
        val isLib: Boolean get() = kind == TargetKind.LIB
        val isBin: Boolean get() = kind == TargetKind.BIN
        val isExample: Boolean get() = kind == TargetKind.EXAMPLE

        val crateRoot: VirtualFile? get() {
            val cached = crateRootCache.get()
            if (cached != null && cached.isValid) return cached
            val file = VirtualFileManager.getInstance().findFileByUrl(crateRootUrl)
            crateRootCache.set(file)
            return file
        }

        private val crateRootCache = AtomicReference<VirtualFile>()
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
    }

    data class ExternCrate(
        /**
         * Name of a crate as appears in `extern crate foo;`
         */
        val name: String,

        /**
         * Root module file (typically `src/lib.rs`)
         */
        val virtualFile: VirtualFile
    ) {
        init {
            check('-' !in name)
        }
    }

    private val targetByCrateRootUrl = packages.flatMap { it.targets }.associateBy { it.crateRootUrl }

    private val externCrates: Collection<ExternCrate> get() = packages.mapNotNull { pkg ->
        val target = pkg.libTarget ?: return@mapNotNull null

        // crate name must be a valid Rust identifier, so map `-` to `_`
        // https://github.com/rust-lang/cargo/blob/ece4e963a3054cdd078a46449ef0270b88f74d45/src/cargo/core/manifest.rs#L299
        val name = target.name.replace("-", "_")
        target.crateRoot?.let { ExternCrate(name, it) }
    }

    /**
     * Searches for the `VirtualFile` of the root mod of the crate
     */
    fun findExternCrateRootByName(crateName: String): VirtualFile? =
        externCrates.orEmpty().find { it.name == crateName }?.virtualFile

    /**
     * If the [file] is a crate root, returns the corresponding [Target]
     */
    fun findTargetForCrateRootFile(file: VirtualFile): Target? {
        val canonicalFile = file.canonicalFile ?: return null
        return targetByCrateRootUrl[canonicalFile.url]
    }

    fun isCrateRoot(file: VirtualFile): Boolean = findTargetForCrateRootFile(file) != null

    /**
     * Combines information about the project structure we got form cargo and information
     * about the standard library that is stored as an IDEA external library
     */
    fun withStdlib(lib: Library): CargoProjectDescription {
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
        return CargoProjectDescription(packages + stdlib)
    }

    val hasStandardLibrary: Boolean get() = packages.any { it.origin == PackageOrigin.STDLIB }

    fun findPackage(name: String): Package? = packages.find { it.name == name }

    companion object {
        fun deserialize(data: CleanCargoMetadata): CargoProjectDescription? {
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
                )
            }

            return CargoProjectDescription(packages)
        }
    }
}

