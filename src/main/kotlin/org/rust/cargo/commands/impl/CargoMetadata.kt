package org.rust.cargo.commands.impl

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.rust.cargo.project.CargoProjectDescription

/**
 * Classes mirroring JSON output of `cargo metadata`.
 * Attribute names and snake_case are crucial.
 *
 * Some information available in JSON is not represented here
 */
object CargoMetadata {
    data class Project(
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


    data class Package(
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
    )


    data class Target(
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
     * A rooted graph of dependencies, represented as adjacency list
     */
    data class Resolve(
        val nodes: List<ResolveNode>
    )


    data class ResolveNode(
        val id: String,

        /**
         * id's of dependent packages
         */
        val dependencies: List<String>
    )

    fun clean(project: Project): CleanCargoMetadata {
        val packageIdToIndex = project.packages.mapIndexed { i, p -> p.id to i }.toMap()
        val fs = LocalFileSystem.getInstance()
        return CleanCargoMetadata(
            project.packages.mapNotNull { it.clean(fs) },
            project.resolve.nodes.map { node ->
                CleanCargoMetadata.DependencyNode(
                    packageIdToIndex[node.id]!!,
                    node.dependencies.map { packageIdToIndex[it]!! }
                )
            }
        )
    }

    private fun Package.clean(fs: LocalFileSystem): CleanCargoMetadata.Package? {
        val root = checkNotNull(fs.refreshAndFindFileByPath(PathUtil.getParentPath(manifest_path))) {
            "`cargo metadata` reported a package which does not exist at `$manifest_path`"
        }
        return CleanCargoMetadata.Package(
            root.url,
            name,
            version,
            targets.mapNotNull { it.clean(root) },
            source
        )
    }

    private fun Target.clean(root: VirtualFile): CleanCargoMetadata.Target? {
        val kind = when (kind) {
            listOf("bin")     -> CargoProjectDescription.TargetKind.BIN
            listOf("example") -> CargoProjectDescription.TargetKind.EXAMPLE
            listOf("test")    -> CargoProjectDescription.TargetKind.TEST
            listOf("bench")   -> CargoProjectDescription.TargetKind.BENCH
            else              ->
                if (kind.any { it.endsWith("lib") })
                    CargoProjectDescription.TargetKind.LIB
                else
                    CargoProjectDescription.TargetKind.UNKNOWN
        }

        val mainFile = if (FileUtil.isAbsolute(src_path))
            root.fileSystem.findFileByPath(src_path)
        else
            root.findFileByRelativePath(src_path)

        // crate name must be a valid Rust identifier, so map `-` to `_`
        // https://github.com/rust-lang/cargo/blob/ece4e963a3054cdd078a46449ef0270b88f74d45/src/cargo/core/manifest.rs#L299
        val name = name.replace("-", "_")
        return mainFile?.let { CleanCargoMetadata.Target(it.url, name, kind) }
    }
}

/**
 * A POD-style representation of [CargoProjectDescription] used as an intermediate representation
 * between `cargo metadata` JSON and [CargoProjectDescription] object graph.
 *
 * Dependency graph is represented via adjacency list, where `Index` is the order of a particular
 * package in `packages` list.
 */
data class CleanCargoMetadata(
    val packages: List<Package>,
    val dependencies: Collection<DependencyNode>
) {
    data class DependencyNode(
        val packageIndex: Int,
        val dependenciesIndexes: Collection<Int>
    )

    data class Package(
        val url: String,
        val name: String,
        val version: String,
        val targets: Collection<Target>,
        val source: String?
    )

    data class Target(
        val url: String,
        val name: String,
        val kind: CargoProjectDescription.TargetKind
    )
}

