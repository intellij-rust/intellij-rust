/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.rust.cargo.project.workspace.CargoWorkspace.TargetKind
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.openapiext.findFileByMaybeRelativePath

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
        val version: Int,

        /**
         * Ids of packages that are members of the cargo workspace
         */
        val workspace_members: List<String>?
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
        val id: PackageId,

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
    ) {
        val cleanKind: TargetKind
            get() = when (kind.singleOrNull()) {
                "bin" -> TargetKind.BIN
                "example" -> TargetKind.EXAMPLE
                "test" -> TargetKind.TEST
                "bench" -> TargetKind.BENCH
                "proc-macro" -> TargetKind.LIB
                else ->
                    if (kind.any { it.endsWith("lib") })
                        TargetKind.LIB
                    else
                        TargetKind.UNKNOWN
            }
    }


    /**
     * A rooted graph of dependencies, represented as adjacency list
     */
    data class Resolve(
        val nodes: List<ResolveNode>
    )


    data class ResolveNode(
        val id: PackageId,

        /**
         * id's of dependent packages
         */
        val dependencies: List<PackageId>
    )

    // The next two things do not belong here,
    // see `machine_message` in Cargo.
    data class Artifact(
        val target: Target,
        val profile: Profile,
        val filenames: List<String>
    ) {
        companion object {
            fun fromJson(json: JsonObject): Artifact? {
                if (json.getAsJsonPrimitive("reason").asString != "compiler-artifact") {
                    return null
                }
                return Gson().fromJson(json, Artifact::class.java)
            }
        }
    }

    data class Profile(
        val test: Boolean
    )

    fun clean(project: Project): CargoWorkspaceData {
        val fs = LocalFileSystem.getInstance()
        val members = project.workspace_members
            ?: error("No `members` key in the `cargo metadata` output.\n" +
            "Your version of Cargo is no longer supported, please upgrade Cargo.")
        return CargoWorkspaceData(
            project.packages.mapNotNull { it.clean(fs, it.id in members) },
            project.resolve.nodes.associate { (id, dependencies) ->
                id to dependencies.toSet()
            }
        )
    }

    private fun Package.clean(fs: LocalFileSystem, isWorkspaceMember: Boolean): CargoWorkspaceData.Package? {
        val root = checkNotNull(fs.refreshAndFindFileByPath(PathUtil.getParentPath(manifest_path))) {
            "`cargo metadata` reported a package which does not exist at `$manifest_path`"
        }
        return CargoWorkspaceData.Package(
            id,
            root.url,
            name,
            version,
            targets.mapNotNull { it.clean(root) },
            source,
            origin = if (isWorkspaceMember) PackageOrigin.WORKSPACE else PackageOrigin.TRANSITIVE_DEPENDENCY
        )
    }

    private fun Target.clean(root: VirtualFile): CargoWorkspaceData.Target? {

        val mainFile = root.findFileByMaybeRelativePath(src_path)

        return mainFile?.let { CargoWorkspaceData.Target(it.url, name, cleanKind) }
    }
}

