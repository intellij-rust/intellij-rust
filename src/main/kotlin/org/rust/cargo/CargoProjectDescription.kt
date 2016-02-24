package org.rust.cargo

import com.intellij.util.PathUtil
import org.rust.cargo.commands.impl.Package
import org.rust.cargo.commands.impl.Project
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
        fun fromCargoMetadata(project: Project): CargoProjectDescription {
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

        private val Package.isModule: Boolean get() = this.source == null

        private fun Package.intoModule(): Module {
            require(isModule)
            return Module(
                rootDirectory,
                name,
                targets.map { target ->
                    val path = if (File(target.src_path).isAbsolute)
                        File(target.src_path).relativeTo(rootDirectory).path
                    else
                        target.src_path

                    check(File(rootDirectory, path).exists())

                    val kind = when (target.kind) {
                        listOf("bin")     -> TargetKind.BIN
                        listOf("example") -> TargetKind.EXAMPLE
                        listOf("test")    -> TargetKind.TEST
                        listOf("bench")   -> TargetKind.BENCH
                        else              ->
                            if (target.kind.any { it.endsWith("lib") }) TargetKind.LIB else TargetKind.UNKNOWN
                    }
                    Target(path, kind)
                }
            )
        }

        private fun Package.intoLibrary(): Library {
            require(!isModule)
            return Library(
                rootDirectory,
                name,
                version
            )
        }

        private val Package.rootDirectory: File get() = File(PathUtil.getParentPath(manifest_path))
    }
}


