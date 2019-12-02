/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.project.workspace.CargoWorkspace.LibKind
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageId
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.toolchain.BuildScriptMessage
import org.rust.openapiext.findFileByMaybeRelativePath
import org.rust.stdext.mapToSet
import java.util.*

private val LOG = Logger.getInstance(CargoMetadata::class.java)

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
        val edition: String?,

        /**
         * Features available in this package.
         * The entry named "default" defines which features are enabled by default.
         */
        val features: Map<String, List<String>>
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
        val edition: String?,

        val doctest: Boolean?
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

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, UNKNOWN
    }

    /**
     * Represents possible variants of generated artifact binary
     * corresponded to `--crate-type` compiler attribute
     *
     * See [linkage](https://doc.rust-lang.org/reference/linkage.html)
     */
    enum class CrateType {
        BIN, LIB, DYLIB, STATICLIB, CDYLIB, RLIB, PROC_MACRO, UNKNOWN
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
         * Id's of dependent packages
         */
        val dependencies: List<PackageId>,

        /**
         * List of dependency info
         *
         * Contains additional info compared with [dependencies] like custom package name.
         */
        val deps: List<Dep>?,

        /**
         * Enabled features (including optional dependencies)
         */
        val features: List<String>?
    )

    data class Dep(
        /**
         * Id of dependent package
         */
        val pkg: PackageId,

        /**
         * Dependency name that should be used in code as extern crate name
         */
        val name: String?
    )

    // The next two things do not belong here,
    // see `machine_message` in Cargo.
    data class Artifact(
        val target: Target,
        val profile: Profile,
        val filenames: List<String>,
        val executable: String?
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

    fun clean(
        project: Project,
        buildScriptsInfo: BuildScriptsInfo,
        buildPlan: CargoBuildPlan?
    ): CargoWorkspaceData {
        val fs = LocalFileSystem.getInstance()
        val members = project.workspace_members
            ?: error(
                "No `workspace_members` key in the `cargo metadata` output.\n" +
                    "Your version of Cargo is no longer supported, please upgrade Cargo."
            )
        val variables = TargetVariables.from(buildPlan)
        val packageIdToNode = project.resolve.nodes.associateBy { it.id }

        return CargoWorkspaceData(
            project.packages.mapNotNull { pkg ->
                // resolve contains all enabled features for each package
                val resolveNode = packageIdToNode[pkg.id]
                if (resolveNode == null) {
                    LOG.error("Could not find package with `id` '${pkg.id}' in `resolve` section of the `cargo metadata` output.")
                }

                val enabledFeatures = resolveNode?.features?.toSet().orEmpty()
                val features = pkg.features.keys.map { feature ->
                    val state = when {
                        enabledFeatures.contains(feature) -> CargoWorkspace.FeatureState.Enabled
                        else -> CargoWorkspace.FeatureState.Disabled
                    }
                    CargoWorkspace.Feature(feature, state)
                }
                val buildScriptMessage = buildScriptsInfo[pkg.id]
                pkg.clean(fs, pkg.id in members, variables, features, buildScriptMessage)
            },
            project.resolve.nodes.associate { (id, dependencies, deps) ->
                val dependencySet = if (deps != null) {
                    deps.mapToSet { (pkgId, name) -> CargoWorkspaceData.Dependency(pkgId, name) }
                } else {
                    dependencies.mapToSet { CargoWorkspaceData.Dependency(it) }
                }
                id to dependencySet
            },
            project.workspace_root
        )
    }

    private fun Package.clean(
        fs: LocalFileSystem,
        isWorkspaceMember: Boolean,
        variables: TargetVariables,
        features: List<CargoWorkspace.Feature>,
        buildScriptMessage: BuildScriptMessage?
    ): CargoWorkspaceData.Package? {
        val root = checkNotNull(fs.refreshAndFindFileByPath(PathUtil.getParentPath(manifest_path))?.canonicalFile) {
            "`cargo metadata` reported a package which does not exist at `$manifest_path`"
        }

        val generatedFeatures = buildScriptMessage?.cfgs.orEmpty()
            .map { CargoWorkspace.Feature(it, CargoWorkspace.FeatureState.Enabled) }

        val env = buildScriptMessage?.env.orEmpty()
            .filter { it.size == 2 }
            .associate { (key, value) -> key to value }

        return CargoWorkspaceData.Package(
            id,
            root.url,
            name,
            version,
            targets.mapNotNull { it.clean(this, root, variables) },
            source,
            origin = if (isWorkspaceMember) PackageOrigin.WORKSPACE else PackageOrigin.TRANSITIVE_DEPENDENCY,
            edition = edition.cleanEdition(),
            features = features + generatedFeatures,
            env = env
        )
    }

    private fun Target.clean(
        pkg: Package,
        root: VirtualFile,
        variables: TargetVariables
    ): CargoWorkspaceData.Target? {
        val mainFile = root.findFileByMaybeRelativePath(src_path)?.canonicalFile
        val outDirPath = variables.getOutDirPath(pkg, this)
        val outDir = outDirPath?.let { root.fileSystem.refreshAndFindFileByPath(it)?.canonicalFile }

        return mainFile?.let {
            CargoWorkspaceData.Target(
                it.url,
                name,
                makeTargetKind(cleanKind, cleanCrateTypes),
                edition.cleanEdition(),
                doctest = doctest ?: true,
                outDirUrl = outDir?.url
            )
        }
    }

    private fun makeTargetKind(target: TargetKind, crateTypes: List<CrateType>): CargoWorkspace.TargetKind {
        return when (target) {
            TargetKind.LIB -> CargoWorkspace.TargetKind.Lib(crateTypes.toLibKinds())
            TargetKind.BIN -> CargoWorkspace.TargetKind.Bin
            TargetKind.TEST -> CargoWorkspace.TargetKind.Test
            TargetKind.EXAMPLE -> if (crateTypes.contains(CrateType.BIN)) {
                CargoWorkspace.TargetKind.ExampleBin
            } else {
                CargoWorkspace.TargetKind.ExampleLib(crateTypes.toLibKinds())
            }
            TargetKind.BENCH -> CargoWorkspace.TargetKind.Bench
            TargetKind.UNKNOWN -> CargoWorkspace.TargetKind.Unknown
        }
    }

    private fun List<CrateType>.toLibKinds(): EnumSet<LibKind> {
        return EnumSet.copyOf(map {
            when (it) {
                CrateType.LIB -> LibKind.LIB
                CrateType.DYLIB -> LibKind.DYLIB
                CrateType.STATICLIB -> LibKind.STATICLIB
                CrateType.CDYLIB -> LibKind.CDYLIB
                CrateType.RLIB -> LibKind.RLIB
                CrateType.PROC_MACRO -> LibKind.PROC_MACRO
                CrateType.BIN -> LibKind.UNKNOWN
                CrateType.UNKNOWN -> LibKind.UNKNOWN
            }
        })
    }

    private fun String?.cleanEdition(): Edition = when (this) {
        Edition.EDITION_2015.presentation -> Edition.EDITION_2015
        Edition.EDITION_2018.presentation -> Edition.EDITION_2018
        else -> Edition.EDITION_2015
    }
}

private class TargetVariables(private val variables: Map<TargetInfo, Map<String, String>>) {

    fun getOutDirPath(pkg: CargoMetadata.Package, target: CargoMetadata.Target): String? =
        getValue(pkg, target, "OUT_DIR")

    fun getValue(pkg: CargoMetadata.Package, target: CargoMetadata.Target, variableName: String): String? {
        val key = TargetInfo(pkg.name, pkg.version, target.kind.toSet())
        return variables[key]?.get(variableName)
    }

    companion object {
        fun from(buildPlan: CargoBuildPlan?): TargetVariables {
            val result = mutableMapOf<TargetInfo, Map<String, String>>()
            for ((packageName, packageVersion, targetKind, variables) in buildPlan?.invocations.orEmpty()) {
                val targetInfo = TargetInfo(packageName, packageVersion, targetKind.toSet())
                result[targetInfo] = variables
            }
            return TargetVariables(result)
        }
    }

    private data class TargetInfo(val name: String, val version: String, val targetKinds: Set<String>)
}
