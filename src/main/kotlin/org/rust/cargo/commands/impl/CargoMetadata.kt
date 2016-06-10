package org.rust.cargo.commands.impl

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.PathUtil
import org.rust.cargo.project.CargoProjectDescription
import org.rust.cargo.project.CargoProjectDescriptionData
import java.io.File

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
        /**
         * id of the main package
         */
        val root: String,
        val nodes: List<ResolveNode>
    )


    data class ResolveNode(
        val id: String,

        /**
         * id's of dependent packages
         */
        val dependencies: List<String>
    )

    fun intoCargoProjectDescriptionData(project: Project): CargoProjectDescriptionData {
        val packageIdToIndex = project.packages.mapIndexed { i, p -> p.id to i }.toMap()
        return CargoProjectDescriptionData(
            packageIdToIndex[project.resolve.root]!!,
            project.packages.map { it.intoCargoProjectDescriptionPackage() },
            project.resolve.nodes.map { node ->
                CargoProjectDescriptionData.DependencyNode(
                    packageIdToIndex[node.id]!!,
                    node.dependencies.map { packageIdToIndex[it]!! }
                )
            }
        )
    }

    private fun Package.intoCargoProjectDescriptionPackage(): CargoProjectDescriptionData.Package {
        val rootDirectory = PathUtil.getParentPath(manifest_path)
        val rootDirFile = File(rootDirectory)
        check(rootDirFile.isAbsolute)
        // crate name must be a valid Rust identifier, so map `-` to `_`
        // https://github.com/rust-lang/cargo/blob/ece4e963a3054cdd078a46449ef0270b88f74d45/src/cargo/core/manifest.rs#L299
        val name = name.replace("-", "_")
        return CargoProjectDescriptionData.Package(
            VfsUtilCore.pathToUrl(rootDirectory),
            name,
            version,
            targets.mapNotNull { it.intoCargoProjectDescriptionTarget(rootDirFile) },
            source
        )
    }

    private fun Target.intoCargoProjectDescriptionTarget(rootDirectory: File): CargoProjectDescriptionData.Target? {
        val path = if (File(src_path).isAbsolute)
            src_path
        else
            File(rootDirectory, src_path).absolutePath

        if (!File(path).exists()) {
            // Some targets of a crate may be not published, ignore them
            return null
        }

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

        return CargoProjectDescriptionData.Target(VfsUtilCore.pathToUrl(path), name, kind)
    }
}
