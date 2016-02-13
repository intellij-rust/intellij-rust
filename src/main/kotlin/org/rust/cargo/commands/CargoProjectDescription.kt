package org.rust.cargo.commands

import com.intellij.util.PathUtil
import org.rust.cargo.commands.impl.Package
import org.rust.cargo.commands.impl.Project
import java.io.File
import java.util.*

class CargoProjectDescription(private val project: Project) {
    val modules: Collection<Module>
    val libraries: Collection<Library>
    val projectName: String

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

        projectName = idToPackage[project.resolve.root]!!.name

        val idToModule = project.packages
            .filter { it.isModule }
            .toMap { pkg ->
                pkg.id to CargoProjectDescription.Module(
                    File(PathUtil.getParentPath(pkg.manifest_path)),
                    pkg.name
                )
            }

        val idToLibrary = project.packages
            .filter { !it.isModule }
            .toMap { pkg ->
                pkg.id to CargoProjectDescription.Library(
                    File(PathUtil.getParentPath(pkg.manifest_path)),
                    pkg.name,
                    pkg.version
                )
            }

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

        modules = idToModule.values
        libraries = idToLibrary.values
    }

    private val Package.isModule: Boolean get() = this.source == null
}


