package org.rust.cargo

import com.intellij.util.PathUtil
import org.rust.cargo.commands.impl.CargoMetadata
import java.io.File
import java.io.Serializable
import java.util.*

class CargoProjectDescription private constructor(
    val packages: Collection<Package>,
    private val rootModule: Package
) {

    class Package(
        val contentRoot: File,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val dependencies: Collection<Package>,
        private val source: String?
    ) {
        init {
            require(contentRoot.exists())
        }

        val isModule: Boolean get() = source == null
    }

    data class Target(
        /**
         * Path to the crate root file, relative to the directory with Cargo.toml
         */
        val path: String,
        val kind: TargetKind
    ) : Serializable {
        init {
            require(!File(path).isAbsolute)
        }
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
    }

    val projectName: String get() = rootModule.name

    companion object {
        fun fromCargoMetadata(project: CargoMetadata.Project): CargoProjectDescription {
            val idToPackage = project.packages.associate { it.id to it }
            val dependenciesMap = project.resolve.nodes.associate { node ->
                idToPackage[node.id]!! to node.dependencies.map { idToPackage[it]!! }
            }

            val alreadyDone = HashMap<CargoMetadata.Package, Package>()
            val inProgress = HashSet<CargoMetadata.Package>()
            /**
             * Recursively constructs a DAG of packages
             */
            fun build(pkg: CargoMetadata.Package): Package {
                if (pkg !in alreadyDone) {
                    check(pkg !in inProgress) {
                        "Circular dependency between in Cargo package: $pkg"
                    }
                    inProgress += pkg
                    pkg.targets.forEach {
                        it.intoTarget(pkg.rootDirectory)
                    }
                    alreadyDone[pkg] = Package(
                        pkg.rootDirectory,
                        pkg.name,
                        pkg.version,
                        pkg.targets.mapNotNull { it.intoTarget(pkg.rootDirectory) },
                        dependenciesMap[pkg]!!.map { build(it) },
                        pkg.source
                    )
                }
                return alreadyDone[pkg]!!
            }

            for (pkg in project.packages) {
                build(pkg)
            }

            val rootModule = alreadyDone[idToPackage[project.resolve.root]]!!
            return CargoProjectDescription(
                alreadyDone.values.toCollection(arrayListOf<Package>()),
                rootModule
            )
        }

        private val CargoMetadata.Package.rootDirectory: File get() = File(PathUtil.getParentPath(manifest_path))

        private fun CargoMetadata.Target.intoTarget(packageRoot: File): Target? {
            val path = if (File(src_path).isAbsolute)
                File(src_path).relativeTo(packageRoot).path
            else
                src_path

            if (!File(packageRoot, path).exists()) {
                // Some targets of a crate may be not published, ignore them
                return null
            }

            val kind = when (kind) {
                listOf("bin")     -> TargetKind.BIN
                listOf("example") -> TargetKind.EXAMPLE
                listOf("test")    -> TargetKind.TEST
                listOf("bench")   -> TargetKind.BENCH
                else              ->
                    if (kind.any { it.endsWith("lib") }) TargetKind.LIB else TargetKind.UNKNOWN
            }
            return Target(path, kind)
        }
    }
}


