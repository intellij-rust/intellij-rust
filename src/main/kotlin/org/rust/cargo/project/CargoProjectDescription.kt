package org.rust.cargo.project

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.containers.HashMap
import com.intellij.util.containers.HashSet


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
        val contentRootPath: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?,
        val dependencies: List<Package>
    ) {
        val isModule: Boolean get() = source == null
        val libTarget: Target? get() = targets.find { it.isLib }

        val contentRoot: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(contentRootPath)
    }

    data class Target(
        /**
         * Absolute path to the crate root file
         */
        val crateRootPath: String,
        val kind: TargetKind
    ) {
        val isLib: Boolean get() = kind == TargetKind.LIB

        val crateRoot: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(crateRootPath)
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
    )

    val externCrates: Collection<ExternCrate> get() = packages.mapNotNull { pkg ->
        pkg.libTarget?.crateRoot?.let { ExternCrate(pkg.name, it) }
    }

    /**
     * Searches for the `VirtualFile` of the root mod of the crate
     */
    fun findExternCrateRootByName(crateName: String): VirtualFile? =
        externCrates.orEmpty().find { it.name == crateName }?.let { it.virtualFile }

    /**
     * Finds a package for this file and returns a (Package, relative path) pair
     */
    fun findPackageForFile(file: VirtualFile): Pair<Package, String>? = packages.asSequence().mapNotNull { pkg ->
        val base = pkg.contentRoot ?: return@mapNotNull null
        val relPath = VfsUtil.getRelativePath(file, base)  ?: return@mapNotNull null
        pkg to relPath
    }.firstOrNull()

    /**
     * If the [file] is a crate root, returns the corresponding [Target]
     */
    fun findTargetForFile(file: VirtualFile): Target? =
        packages.asSequence()
            .flatMap { it.targets.asSequence() }
            .find { it.crateRoot == file }

    fun isCrateRoot(file: VirtualFile): Boolean = findTargetForFile(file) != null

    fun findFileInPackage(packageName: String, relPath: String): VirtualFile? =
        packages.find { it.name == packageName }?.let {
            it.contentRoot?.findFileByRelativePath(relPath)
        }

    fun withAdditionalPackages(additionalPackages: Collection<Pair<String, VirtualFile>>): CargoProjectDescription {
        val stdlibPackages = additionalPackages.map {
            val (crateName, crateRoot) = it
            Package(
                contentRootPath = crateRoot.parent.url,
                name = crateName,
                version = "",
                targets = listOf(Target(crateRoot.url, kind = TargetKind.LIB)),
                source = null,
                dependencies = emptyList()
            )
        }
        return CargoProjectDescription(packages + stdlibPackages)
    }

    companion object {
        fun deserialize(data: CargoProjectDescriptionData): CargoProjectDescription? {
            val dependenciesMap = data.dependencies.associate { node ->
                val pkg = data.packages.getOrNull(node.packageIndex) ?: return null
                val deps = node.dependenciesIndexes.map { data.packages.getOrNull(it) ?: return null }
                pkg to deps
            }

            val alreadyDone = HashMap<CargoProjectDescriptionData.Package, Package>()
            val inProgress = HashSet<CargoProjectDescriptionData.Package>()

            /**
             * Recursively constructs a DAG of packages
             */
            fun build(pkg: CargoProjectDescriptionData.Package): Package? {
                if (pkg !in alreadyDone) {
                    check(pkg !in inProgress) {
                        "Circular dependency in package: $pkg"
                    }
                    inProgress += pkg
                    alreadyDone[pkg] = Package(
                        pkg.contentRootUrl,
                        pkg.name,
                        pkg.version,
                        pkg.targets.map { CargoProjectDescription.Target(it.url, it.kind) },
                        pkg.source,
                        dependenciesMap[pkg].orEmpty().map { build(it) ?: return null }
                    )
                }
                return alreadyDone[pkg]!!
            }

            for (pkg in data.packages) {
                build(pkg)
            }

            return CargoProjectDescription(alreadyDone.values)
        }
    }
}

