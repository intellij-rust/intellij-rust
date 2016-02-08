package org.rust.cargo.project

import com.google.gson.Gson
import com.intellij.util.PathUtil
import org.rust.cargo.project.module.RustModuleType
import java.io.File
import java.util.*

class CargoMetadata private constructor(private val project: Project) {
    val modules: Collection<Module>
    val libraries: Collection<Library>
    val projectName: String

    inner class Module(val contentRoot: File,
                       val name: String,
                       val moduleType: String,
                       val moduleDependencies: MutableCollection<Module> = ArrayList(),
                       val libraryDependencies: MutableCollection<Library> = ArrayList())

    inner class Library(val contentRoot: File,
                        val name: String,
                        val version: String)

    companion object {
        fun fromJson(json: String): CargoMetadata {
            val project = Gson().fromJson(
                json,
                Project::class.java
            )
            return CargoMetadata(project)
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
                pkg.id to Module(
                    File(PathUtil.getParentPath(pkg.manifest_path)),
                    pkg.name,
                    RustModuleType.MODULE_TYPE_ID
                )
            }

        val idToLibrary = project.packages
            .filter { !it.isModule }
            .toMap { pkg ->
                pkg.id to Library(
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
}


/**
 * Classes mirroring JSON output of `cargo metadata`.
 * Attribute names and snake_case are crucial.
 *
 * Some information available in JSON is not represented here
 */
private data class Project(
    /**
     * All packages, including dependencies
     */
    val packages: List<Package>,

    /**
     * A graph of dependencies
     */
    val resolve: Resolve,

    /**
     * Version of the format (currently 1)
     */
    val version: Int
)


private data class Package(
    val name: String,

    /**
     * SemVer version
     */
    val version: String,

    /**
     * Where did this package comes from? Local file system, crates.io, github repository.
     *
     * Will be `null` for the root package and path dependencies.
     */
    val source: String?,

    /**
     * A unique id.
     * There may be several packages with the same name, but different version/source.
     * The triple (name, version, source) is unique.
     */
    val id: String,

    /**
     * Path to Cargo.toml
     */
    val manifest_path: String,

    /**
     * Artifacts that can be build from this package.
     * This is a list of crates that can be build from the package.
     */
    val targets: List<Target>
) {

    val isModule: Boolean get() = source == null

}


private data class Target(
    /**
     * Kind of a target. Can be a singleton list ["bin"],
     * ["example], ["test"], ["example"], ["custom-build"], ["bench"].
     *
     * Can also be a list of one or more of "lib", "rlib", "dylib", "staticlib"
     */
    val kind: List<String>,

    /**
     * Name
     */
    val name: String,

    /**
     * Path to the root module of the crate (aka crate root)
     */
    val src_path: String
)


/**
 * A rooted DAG of dependencies, represented as adjacency list
 */
private data class Resolve(
    /**
     * id of the main package
     */
    val root: String,
    val nodes: List<ResolveNode>
)


private data class ResolveNode(
    val id: String,

    /**
     * id's of dependent packages
     */
    val dependencies: List<String>
)
