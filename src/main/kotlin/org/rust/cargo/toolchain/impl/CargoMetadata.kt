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
import org.rust.cargo.project.workspace.CargoWorkspace.*
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
        val workspace_members: List<String>?,

        /**
         * Path to workspace root folder. Can be null for old cargo version
         */
        // BACKCOMPAT: Rust 1.23: use not nullable type here
        val workspace_root: String?
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
        val targets: List<Target>,

        /**
         * Code edition of current package.
         *
         * Can be "2015", "2018" or null. Null value can be got from old version of cargo
         * so it is the same as "2015"
         */
        val edition: String?
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
        val src_path: String,

        /**
         * List of crate types
         *
         * See [linkage](https://doc.rust-lang.org/reference/linkage.html)
         */
        val crate_types: List<String>,

        /**
         * Code edition of current target.
         *
         * Can be "2015", "2018" or null. Null value can be got from old version of cargo
         * so it is the same as "2015"
         */
        val edition: String?
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

        val cleanCrateTypes: List<CrateType>
            get() = crate_types.map {
                when (it) {
                    "bin" -> CrateType.BIN
                    "lib" -> CrateType.LIB
                    "dylib" -> CrateType.DYLIB
                    "staticlib" -> CrateType.STATICLIB
                    "cdylib" -> CrateType.CDYLIB
                    "rlib" -> CrateType.RLIB
                    "proc-macro" -> CrateType.PROC_MACRO
                    else -> CrateType.UNKNOWN
                }
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
            },
            project.workspace_root
        )
    }

    private fun Package.clean(fs: LocalFileSystem, isWorkspaceMember: Boolean): CargoWorkspaceData.Package? {
        val root = checkNotNull(fs.refreshAndFindFileByPath(PathUtil.getParentPath(manifest_path))?.canonicalFile) {
            "`cargo metadata` reported a package which does not exist at `$manifest_path`"
        }
        return CargoWorkspaceData.Package(
            id,
            root.url,
            name,
            version,
            targets.mapNotNull { it.clean(root) },
            source,
            origin = if (isWorkspaceMember) PackageOrigin.WORKSPACE else PackageOrigin.TRANSITIVE_DEPENDENCY,
            edition = edition.cleanEdition()
        )
    }

    private fun Target.clean(root: VirtualFile): CargoWorkspaceData.Target? {
        val mainFile = root.findFileByMaybeRelativePath(src_path)?.canonicalFile
        return mainFile?.let {
            CargoWorkspaceData.Target(it.url, name, cleanKind, cleanCrateTypes, edition.cleanEdition())
        }
    }

    private fun String?.cleanEdition(): Edition = when (this) {
        "2015" -> Edition.EDITION_2015
        "2018" -> Edition.EDITION_2018
        else -> Edition.EDITION_2015
    }
}
