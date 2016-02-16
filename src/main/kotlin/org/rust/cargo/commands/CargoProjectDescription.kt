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

    init {
        val idToPackage = project.packages.toMap { it.id to it }

        val dependenciesMap = project.resolve.nodes.toMap { node ->
            idToPackage[node.id]!! to node.dependencies.map { idToPackage[it]!! }
        }

        val (modPackages, libPackages) = project.packages.partition { it.isModule }

        val idToModule = modPackages.toMap { it.id to it.intoModule() }
        val idToLibrary = libPackages.toMap { it.id to it.intoLibrary() }

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
            name
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


