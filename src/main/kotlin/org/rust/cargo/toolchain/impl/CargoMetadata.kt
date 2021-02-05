/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.workspace.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.project.workspace.CargoWorkspace.LibKind
import org.rust.openapiext.findFileByMaybeRelativePath
import org.rust.stdext.mapToSet
import java.util.*

private val LOG = Logger.getInstance(CargoMetadata::class.java)

typealias PathConverter = (String) -> String

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
        val workspace_members: List<String>,

        /**
         * Path to workspace root folder. Can be null for old cargo version
         */
        val workspace_root: String
    ) {
        fun convertPaths(converter: PathConverter): Project = copy(
            packages = packages.map { it.convertPaths(converter) },
            workspace_root = converter(workspace_root)
        )
    }

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
         * Features available in this package (excluding optional dependencies).
         * The entry named "default" defines which features are enabled by default.
         */
        val features: Map<FeatureName, List<FeatureDep>>,

        /**
         * Dependencies as they listed in the package `Cargo.toml`, without package resolution or
         * any additional data.
         */
        val dependencies: List<RawDependency>
    ) {
        fun convertPaths(converter: PathConverter): Package = copy(
            manifest_path = converter(manifest_path),
            targets = targets.map { it.convertPaths(converter) }
        )
    }

    data class RawDependency(
        /** A `package` name (non-normalized) of the dependency */
        val name: String,
        val kind: String?,
        val target: String?,
        val optional: Boolean,
        val uses_default_features: Boolean,
        val features: List<String>
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

        /**
         * Indicates whether or not
         * [documentation examples](https://doc.rust-lang.org/rustdoc/documentation-tests.html)
         * are tested by default by `cargo test`. This is only relevant for libraries, it has
         * no effect on other targets. The default is `true` for the library.
         *
         * See [docs](https://doc.rust-lang.org/cargo/reference/cargo-targets.html#the-doctest-field)
         */
        val doctest: Boolean?,

        /**
         * Specifies which package features the target needs in order to be built. This is only relevant
         * for the `[[bin]]`, `[[bench]]`, `[[test]]`, and `[[example]]` sections, it has no effect on `[lib]`.
         *
         * See [docs](https://doc.rust-lang.org/cargo/reference/cargo-targets.html#the-required-features-field)
         */
        @SerializedName("required-features")
        val required_features: List<String>?
    ) {
        val cleanKind: TargetKind
            get() = when (kind.singleOrNull()) {
                "bin" -> TargetKind.BIN
                "example" -> TargetKind.EXAMPLE
                "test" -> TargetKind.TEST
                "bench" -> TargetKind.BENCH
                "proc-macro" -> TargetKind.LIB
                "custom-build" -> TargetKind.CUSTOM_BUILD
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

        fun convertPaths(converter: PathConverter): Target = copy(
            src_path = converter(src_path)
        )
    }

    enum class TargetKind {
        LIB, BIN, TEST, EXAMPLE, BENCH, CUSTOM_BUILD, UNKNOWN
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
        val name: String?,

        /**
         * Used to distinguish `[dependencies]`, [dev-dependencies]` and `[build-dependencies]`.
         * It's a list because a dependency can be used in both `[dependencies]` and `[build-dependencies]`.
         * `null` on old cargo only.
         */
        val dep_kinds: List<DepKindInfo>?
    )

    data class DepKindInfo(
        val kind: String?,
        val target: String?
    ) {
        fun clean(): CargoWorkspace.DepKindInfo = CargoWorkspace.DepKindInfo(
            when (kind) {
                "dev" -> CargoWorkspace.DepKind.Development
                "build" -> CargoWorkspace.DepKind.Build
                else -> CargoWorkspace.DepKind.Normal
            },
            target
        )
    }

    // The next two things do not belong here,
    // see `machine_message` in Cargo.
    data class Artifact(
        val package_id: PackageId?,
        val target: Target,
        val profile: Profile,
        val filenames: List<String>,
        val executable: String?
    ) {

        val executables: List<String>
            get() {
                return if (executable != null) {
                    listOf(executable)
                } else {
                    /**
                     * `.dSYM` and `.pdb` files are binaries, but they should not be used when starting debug session.
                     * Without this filtering, CLion shows error message about several binaries
                     * in case of disabled build tool window
                     */
                    // BACKCOMPAT: Cargo 0.34.0
                    filenames.filter { !it.endsWith(".dSYM") && !it.endsWith(".pdb") }
                }
            }

        fun convertPaths(converter: PathConverter): Artifact = copy(
            target = target.convertPaths(converter)
        )

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
        buildScriptsInfo: BuildScriptsInfo? = null,
        buildPlan: CargoBuildPlan? = null
    ): CargoWorkspaceData {
        val fs = LocalFileSystem.getInstance()
        val members = project.workspace_members
        val variables = PackageVariables.from(buildPlan)
        val packageIdToNode = project.resolve.nodes.associateBy { it.id }
        return CargoWorkspaceData(
            project.packages.mapNotNull { pkg ->
                // resolve contains all enabled features for each package
                val resolveNode = packageIdToNode[pkg.id]
                if (resolveNode == null) {
                    LOG.error("Could not find package with `id` '${pkg.id}' in `resolve` section of the `cargo metadata` output.")
                }
                val enabledFeatures = resolveNode?.features.orEmpty().toSet() // features enabled by Cargo
                val buildScriptMessage = buildScriptsInfo?.get(pkg.id)
                pkg.clean(fs, pkg.id in members, variables, enabledFeatures, buildScriptMessage)
            },
            project.resolve.nodes.associate { node ->
                val dependencySet = if (node.deps != null) {
                    node.deps.mapToSet { (pkgId, name, depKinds) ->
                        val depKindsLowered = depKinds?.map { it.clean() }
                            ?: listOf(CargoWorkspace.DepKindInfo(CargoWorkspace.DepKind.Unclassified))
                        CargoWorkspaceData.Dependency(pkgId, name, depKindsLowered)
                    }
                } else {
                    node.dependencies.mapToSet { CargoWorkspaceData.Dependency(it) }
                }
                node.id to dependencySet
            },
            project.packages.associate { it.id to it.dependencies },
            project.workspace_root
        )
    }

    private fun Package.clean(
        fs: LocalFileSystem,
        isWorkspaceMember: Boolean,
        variables: PackageVariables,
        enabledFeatures: Set<String>,
        buildScriptMessage: BuildScriptMessage?
    ): CargoWorkspaceData.Package? {
        val root = fs.refreshAndFindFileByPath(PathUtil.getParentPath(manifest_path))
            ?.let { if (isWorkspaceMember) it else it.canonicalFile }
        checkNotNull(root) { "`cargo metadata` reported a package which does not exist at `$manifest_path`" }

        val features = features.toMutableMap()

        // Optional dependencies are features implicitly
        for (dependency in dependencies) {
            if (dependency.optional && dependency.name !in features) {
                features[dependency.name] = emptyList()
            }
        }

        val cfgOptions = buildScriptMessage?.cfgs?.let { CfgOptions.parse(it) }

        val env = buildScriptMessage?.env.orEmpty()
            .filter { it.size == 2 }
            .associate { (key, value) -> key to value }

        val outDirPath = buildScriptMessage?.out_dir ?: variables.getOutDirPath(this)
        val outDir = outDirPath
            ?.let { root.fileSystem.refreshAndFindFileByPath(it) }
            ?.let { if (isWorkspaceMember) it else it.canonicalFile }

        return CargoWorkspaceData.Package(
            id,
            root.url,
            name,
            version,
            targets.mapNotNull { it.clean(root, isWorkspaceMember) },
            source,
            origin = if (isWorkspaceMember) PackageOrigin.WORKSPACE else PackageOrigin.DEPENDENCY,
            edition = edition.cleanEdition(),
            features = features,
            enabledFeatures = enabledFeatures,
            cfgOptions = cfgOptions,
            env = env,
            outDirUrl = outDir?.url
        )
    }

    private fun Target.clean(root: VirtualFile, isWorkspaceMember: Boolean): CargoWorkspaceData.Target? {
        val mainFile = root.findFileByMaybeRelativePath(src_path)
            ?.let { if (isWorkspaceMember) it else it.canonicalFile }

        return mainFile?.let {
            CargoWorkspaceData.Target(
                it.url,
                name,
                makeTargetKind(cleanKind, cleanCrateTypes),
                edition.cleanEdition(),
                doctest = doctest ?: true,
                requiredFeatures = required_features.orEmpty()
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
            TargetKind.CUSTOM_BUILD -> CargoWorkspace.TargetKind.CustomBuild
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

    fun Project.replacePaths(replacer: (String) -> String): Project =
        copy(
            packages = packages.map { it.replacePaths(replacer) },
            workspace_root = replacer(workspace_root)
        )

    private fun Package.replacePaths(replacer: (String) -> String): Package =
        copy(
            manifest_path = replacer(manifest_path),
            targets = targets.map { it.replacePaths(replacer) }
        )

    private fun Target.replacePaths(replacer: (String) -> String): Target =
        copy(src_path = replacer(src_path))
}

private class PackageVariables(private val variables: Map<PackageInfo, Map<String, String>>) {

    fun getOutDirPath(pkg: CargoMetadata.Package): String? = getValue(pkg, "OUT_DIR")

    fun getValue(pkg: CargoMetadata.Package, variableName: String): String? {
        val key = PackageInfo(pkg.name, pkg.version)
        return variables[key]?.get(variableName)
    }

    companion object {
        fun from(buildPlan: CargoBuildPlan?): PackageVariables {
            val result = mutableMapOf<PackageInfo, Map<String, String>>()
            for ((packageName, packageVersion, compileMode, variables) in buildPlan?.invocations.orEmpty()) {
                if (compileMode != "run-custom-build") continue
                val targetInfo = PackageInfo(packageName, packageVersion)
                result[targetInfo] = variables
            }
            return PackageVariables(result)
        }
    }

    private data class PackageInfo(val name: String, val version: String)
}
