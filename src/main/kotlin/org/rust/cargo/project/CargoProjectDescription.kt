package org.rust.cargo.project

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
        val contentRootUrl: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?,
        val dependencies: List<Package>
    ) {
        val isModule: Boolean get() = source == null
        val libTarget: Target? get() = targets.find { it.isLib }

        val virtualFile: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl)
    }

    data class Target(
        /**
         * Absolute path to the crate root file
         */
        val url: String,
        val kind: TargetKind
    ) {
        val isLib: Boolean get() = kind == TargetKind.LIB

        val virtualFile: VirtualFile? get() = VirtualFileManager.getInstance().findFileByUrl(url)
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
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

