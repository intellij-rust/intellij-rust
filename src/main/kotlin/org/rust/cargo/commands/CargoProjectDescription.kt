package org.rust.cargo.commands

import com.intellij.util.PathUtil
import org.rust.cargo.commands.impl.Package
import org.rust.cargo.commands.impl.Project
import java.io.File
import java.util.*

class CargoProjectDescription(private val project: Project) {
    val modules: Collection<Module>
    val libraries: Collection<Library>
    val projectName: String get() = rootModule.name

    private val rootModule: Module

    class Module(val contentRoot: File,
                 val name: String,
                 val targets: Collection<Target>,
                 val moduleDependencies: MutableCollection<Module> = ArrayList(),
                 val libraryDependencies: MutableCollection<Library> = ArrayList()) {
        init {
            require(contentRoot.exists())
        }
    }

    class Library(val contentRoot: File,
                  val name: String,
                  val version: String) {
        init {
            require(contentRoot.exists())
        }
    }

    class Target(val rootModFile: File,
                 val kind: TargetKind) {
        init {
            require(rootModFile.exists())
        }
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
    }

    init {
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

        rootModule = idToModule[project.resolve.root]!!
        modules = idToModule.values
        libraries = idToLibrary.values
    }

    private val Package.isModule: Boolean get() = this.source == null

    private val Package.rootDirectory: File get() = File(PathUtil.getParentPath(manifest_path))

    private fun Package.intoModule(): Module {
        require(isModule)
        return Module(
            rootDirectory,
            name,
            targets.map { target ->
                val file = if (File(target.src_path).isAbsolute)
                    File(target.src_path)
                else
                    File(rootDirectory, target.src_path)

                val kind = when (target.kind) {
                    listOf("bin")     -> TargetKind.BIN
                    listOf("example") -> TargetKind.EXAMPLE
                    listOf("test")    -> TargetKind.TEST
                    listOf("bench")   -> TargetKind.BENCH
                    else              -> if (target.kind.any { it.endsWith("lib") }) TargetKind.LIB else TargetKind.UNKNOWN
                }
                CargoProjectDescription.Target(
                    file,
                    kind
                )
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

}


