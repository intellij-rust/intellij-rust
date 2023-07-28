/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.impl

import com.fasterxml.jackson.annotation.JsonProperty
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.*
import com.intellij.util.PathUtil
import com.intellij.util.text.SemVer
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.workspace.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.cargo.project.workspace.CargoWorkspace.LibKind
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.findFileByMaybeRelativePath
import org.rust.stdext.HashCode
import org.rust.stdext.mapToSet
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.*
import kotlin.io.path.exists

private val LOG: Logger = logger<CargoMetadata>()

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
         * Path to workspace root folder
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

        val authors: List<String>,

        val description: String?,

        val repository: String?,

        val license: String?,

        val license_file: String?,

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
        /** Non-null if renamed. Non-normalized (i.e. can contain "-") */
        val rename: String?,
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
        @Suppress("KDocUnresolvedReference")
        @JsonProperty("required-features")
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
         * Used to distinguish `[dependencies]`, `[dev-dependencies]` and `[build-dependencies]`.
         * It's a list because a dependency can be used in both `[dependencies]` and `[build-dependencies]`.
         * `null` on old cargo only.
         */
        @Suppress("KDocUnresolvedReference")
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

    fun clean(
        project: Project,
        buildMessages: BuildMessages? = null
    ): CargoWorkspaceData {
        val fs = LocalFileSystem.getInstance()
        val workspaceRoot = fs.refreshAndFindFileByPath(project.workspace_root)
        requireNotNull(workspaceRoot) { "`cargo metadata` reported a workspace path which does not exist at `${project.workspace_root}`" }

        val members = project.workspace_members
        val packageIdToNode = project.resolve.nodes.associateBy { it.id }

        val packages = project.packages.map { pkg ->
            // resolve contains all enabled features for each package
            val resolveNode = packageIdToNode[pkg.id]
            if (resolveNode == null) {
                LOG.error("Could not find package with `id` '${pkg.id}' in `resolve` section of the `cargo metadata` output.")
            }
            val enabledFeatures = resolveNode?.features.orEmpty().toSet() // features enabled by Cargo
            val pkgBuildMessages = buildMessages?.get(pkg.id).orEmpty()
            pkg.clean(fs, pkg.id in members, enabledFeatures, pkgBuildMessages)
        }

        return CargoWorkspaceData(
            packages,
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
            workspaceRoot.url
        )
    }

    private fun Package.clean(
        fs: LocalFileSystem,
        isWorkspaceMember: Boolean,
        enabledFeatures: Set<String>,
        buildMessages: List<CompilerMessage>
    ): CargoWorkspaceData.Package {
        val rootPath = PathUtil.getParentPath(manifest_path)
        val root = fs.refreshAndFindFileByPath(rootPath)
            ?.let { if (isWorkspaceMember) it else it.canonicalFile }
            ?: throw CargoMetadataException("`cargo metadata` reported a package which does not exist at `$manifest_path`")

        val features = features.toMutableMap()

        // Backcompat Cargo 1.59.0: optional dependencies are features implicitly.
        // Since 1.60.0 these implicit features are returned from cargo as usual
        val allFeatureDependencies = features.values.flatten().toSet()
        for (dependency in dependencies) {
            val featureName = dependency.rename ?: dependency.name
            if (dependency.optional && featureName !in features) {
                val depFeatureName = "dep:$featureName"
                if (depFeatureName !in allFeatureDependencies) {
                    features[featureName] = listOf(depFeatureName)
                }
            }
        }

        val buildScriptMessage = buildMessages.find { it is BuildScriptMessage } as? BuildScriptMessage
        val procMacroArtifact = getProcMacroArtifact(buildMessages)

        val cfgOptions = buildScriptMessage?.cfgs?.let { CfgOptions.parse(it) }

        val envFromBuildscript = buildScriptMessage?.env.orEmpty()
            .filter { it.size == 2 }
            .associate { (key, value) -> key to value }

        val semver = SemVer.parseFromText(version)

        // https://doc.rust-lang.org/cargo/reference/environment-variables.html#environment-variables-cargo-sets-for-crates
        val env: Map<String, String> = envFromBuildscript + mapOf(
            "CARGO_MANIFEST_DIR" to rootPath,
            "CARGO" to "cargo", // TODO get from toolchain
            "CARGO_PKG_VERSION" to version,
            "CARGO_PKG_VERSION_MAJOR" to semver?.major?.toString().orEmpty(),
            "CARGO_PKG_VERSION_MINOR" to semver?.minor?.toString().orEmpty(),
            "CARGO_PKG_VERSION_PATCH" to semver?.patch?.toString().orEmpty(),
            "CARGO_PKG_VERSION_PRE" to semver?.preRelease.orEmpty(),
            "CARGO_PKG_AUTHORS" to authors.joinToString(separator = ";"),
            "CARGO_PKG_NAME" to name,
            "CARGO_PKG_DESCRIPTION" to description.orEmpty(),
            "CARGO_PKG_REPOSITORY" to repository.orEmpty(),
            "CARGO_PKG_LICENSE" to license.orEmpty(),
            "CARGO_PKG_LICENSE_FILE" to license_file.orEmpty(),
            "CARGO_CRATE_NAME" to name.replace('-', '_'),
        )

        val outDir = buildScriptMessage?.out_dir
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
            outDirUrl = outDir?.url,
            procMacroArtifact = procMacroArtifact
        )
    }

    private fun getProcMacroArtifact(buildMessages: List<CompilerMessage>): CargoWorkspaceData.ProcMacroArtifact? {
        val procMacroArtifacts = buildMessages
            .filterIsInstance<CompilerArtifactMessage>()
            .filter {
                it.target.kind.contains("proc-macro") && it.target.crate_types.contains("proc-macro")
            }

        val procMacroArtifactPath = procMacroArtifacts
            .flatMap { it.filenames }
            .find { file -> DYNAMIC_LIBRARY_EXTENSIONS.any { file.endsWith(it) } }

        return procMacroArtifactPath?.let {
            val originPath = Path.of(procMacroArtifactPath)

            val hash = try {
                HashCode.ofFile(originPath)
            } catch (e: IOException) {
                LOG.warn(e)
                return@let null
            }

            val path = copyProcMacroArtifactToTempDir(originPath, hash)

            CargoWorkspaceData.ProcMacroArtifact(path, hash)
        }
    }

    /**
     * Copy the artifact to a temporary directory in order to allow a user to overwrite or delete the artifact.
     *
     * It's `@Synchronized` because it can be called from different IDEA projects simultaneously,
     * and different projects may want to write the same files
     */
    @Synchronized
    private fun copyProcMacroArtifactToTempDir(originPath: Path, hash: HashCode): Path {
        return try {
            val temp = RsPathManager.tempPluginDirInSystem().resolve("proc_macros")
            Files.createDirectories(temp) // throws IOException
            val filename = originPath.fileName.toString()
            val extension = PathUtil.getFileExtension(filename)
            val targetPath = temp.resolve("$filename.$hash.$extension")
            if (!targetPath.exists() || Files.size(originPath) != Files.size(targetPath)) {
                Files.copy(originPath, targetPath, StandardCopyOption.REPLACE_EXISTING) // throws IOException
            }
            targetPath
        } catch (e: IOException) {
            LOG.warn(e)
            originPath
        }
    }

    private val DYNAMIC_LIBRARY_EXTENSIONS: List<String> = listOf(".dll", ".so", ".dylib")

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
        Edition.EDITION_2021.presentation -> Edition.EDITION_2021
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
