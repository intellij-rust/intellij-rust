package org.rust.cargo.project

import com.intellij.openapi.vfs.VfsUtil
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
        val dependencies: List<Package>
    ) {
        val isModule: Boolean get() = source == null
        val libTarget: Target? get() = targets.find { it.isLib }

        val contentRoot: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl)
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

    val externCrates: Collection<ExternCrate> get() = packages.mapNotNull { pkg ->
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
        externCrates.orEmpty().find { it.name == crateName }?.let { it.virtualFile }

    /**
     * Finds a package for this file and returns a (Package, relative path) pair
     */
    fun findPackageForFile(file: VirtualFile): Pair<Package, String>? {
        val canonicalFile = file.canonicalFile ?: return null

        return packages.asSequence().mapNotNull { pkg ->
            val base = pkg.contentRoot ?: return@mapNotNull null
            val relPath = VfsUtil.getRelativePath(canonicalFile, base) ?: return@mapNotNull null
            pkg to relPath
        }.firstOrNull()
    }

    /**
     * If the [file] is a crate root, returns the corresponding [Target]
     */
    fun findTargetForFile(file: VirtualFile): Target? {
        val canonicalFile = file.canonicalFile ?: return null
        return targetByCrateRootUrl[canonicalFile.url]
    }

    fun isCrateRoot(file: VirtualFile): Boolean = findTargetForFile(file) != null

    fun withAdditionalPackages(additionalPackages: Collection<Pair<String, VirtualFile>>): CargoProjectDescription {
        val stdlibPackages = additionalPackages.map {
            val (crateName, crateRoot) = it
            Package(
                contentRootUrl = crateRoot.parent.url,
                name = crateName,
                version = "",
                targets = listOf(Target(crateRoot.url, name = crateName, kind = TargetKind.LIB)),
                source = null,
                dependencies = emptyList()
            )
        }
        return CargoProjectDescription(packages + stdlibPackages)
    }

    val hasStandardLibrary: Boolean get() = findExternCrateRootByName(AutoInjectedCrates.std) != null

    companion object {
        fun deserialize(data: CleanCargoMetadata): CargoProjectDescription? {
            // Packages form mostly a DAG. "Why mostly?", you say.
            // Well, a dev-dependency `X` of package `P` can depend on the `P` itself.
            // This is ok, because cargo can compile `P` (without `X`, because dev-deps
            // are used only for tests), then `X`, and then `P`s tests. So we need to
            // handle cycles here, and it is the justification for the trick with `mutableDeps`.

            val (packages, mutableDeps) = data.packages.map { pkg ->
                val deps: MutableList<Package> = ArrayList()
                Package(
                    pkg.url,
                    pkg.name,
                    pkg.version,
                    pkg.targets.map { Target(it.url, it.name, it.kind) },
                    pkg.source,
                    deps
                ) to deps
            }.unzip()

            for ((packageIndex, dependenciesIndexes) in data.dependencies) {
                val deps = mutableDeps.getOrNull(packageIndex) ?: return null
                dependenciesIndexes.mapTo(deps) {
                    packages.getOrNull(it) ?: return null
                }
            }

            return CargoProjectDescription(packages)
        }
    }
}

