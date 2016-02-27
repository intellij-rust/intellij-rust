package org.rust.cargo

import com.intellij.util.PathUtil
import org.rust.cargo.commands.impl.CargoMetadata
import java.io.File
import java.util.*

class CargoProjectDescription private constructor(
    val modules: Collection<Module>,
    val libraries: Collection<Library>,
    private val rootModule: Module
) {

    class Module(
        val contentRoot: File,
        val name: String,
        val targets: Collection<Target>,
        val moduleDependencies: MutableCollection<Module> = ArrayList(),
        val libraryDependencies: MutableCollection<Library> = ArrayList()
    ) {
        init {
            require(contentRoot.exists())
        }
    }

    class Library(
        val contentRoot: File,
        val name: String,
        val version: String
    ) {

        init {
            require(contentRoot.exists())
        }
    }

    class Target(
        /**
         * Path to the crate root file, relative to the directory with Cargo.toml
         */
        val path: String,
        val kind: TargetKind
    ) {

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

            val (modPackages, libPackages) = project.packages.partition { it.isModule }

            val idToModule = modPackages.associate { it.id to it.intoModule() }
            val idToLibrary = libPackages.associate { it.id to it.intoLibrary() }

            for ((pkg, deps) in dependenciesMap) {
                val module = idToModule[pkg.id] ?: continue
                for (dep in deps) {
                    idToModule[dep.id]?.let {
                        module.moduleDependencies.add(it)
                    }

                    idToLibrary[dep.id]?.let {
                        module.libraryDependencies.add(it)
                    }
                }
            }

            return CargoProjectDescription(
                idToModule.values,
                idToLibrary.values,
                idToModule[project.resolve.root]!!
            )
        }

        private val CargoMetadata.Package.isModule: Boolean get() = this.source == null

        private fun CargoMetadata.Package.intoModule(): Module {
            require(isModule)
            return Module(
                rootDirectory,
                name,
                targets.map { it.intoTarget(rootDirectory) }
            )
        }

        private fun CargoMetadata.Package.intoLibrary(): Library {
            require(!isModule)
            return Library(
                rootDirectory,
                name,
                version
            )
        }

        private val CargoMetadata.Package.rootDirectory: File get() = File(PathUtil.getParentPath(manifest_path))

        private fun CargoMetadata.Target.intoTarget(packageRoot: File): Target {
            val path = if (File(src_path).isAbsolute)
                File(src_path).relativeTo(packageRoot).path
            else
                src_path

            check(File(packageRoot, path).exists())

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


