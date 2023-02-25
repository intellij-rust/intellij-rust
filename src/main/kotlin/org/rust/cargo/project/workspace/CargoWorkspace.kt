/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.ThreeState
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CargoConfig
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.impl.UserDisabledFeatures
import org.rust.cargo.project.workspace.PackageOrigin.*
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.openapiext.CachedVirtualFile
import org.rust.stdext.applyWithSymlink
import org.rust.stdext.mapToSet
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * Rust project model represented roughly in the same way as in Cargo itself.
 *
 * [CargoProjectsService] manages workspaces.
 */
interface CargoWorkspace {
    val manifestPath: Path
    val contentRoot: Path get() = manifestPath.parent

    val workspaceRoot: VirtualFile?

    val cfgOptions: CfgOptions
    val cargoConfig: CargoConfig

    /**
     * Flatten list of packages including workspace members, dependencies, transitive dependencies
     * and stdlib. Use `packages.filter { it.origin == PackageOrigin.WORKSPACE }` to
     * obtain workspace members.
     */
    val packages: Collection<Package>

    val featureGraph: FeatureGraph

    fun findPackageById(id: PackageId): Package? = packages.find { it.id == id }
    fun findPackageByName(name: String, isStd: ThreeState = ThreeState.UNSURE): Package? = packages.find {
        if (it.name != name && it.normName != name) return@find false
        when (isStd) {
            ThreeState.YES -> it.origin == STDLIB
            ThreeState.NO -> it.origin == WORKSPACE || it.origin == DEPENDENCY
            ThreeState.UNSURE -> true
        }
    }

    fun findTargetByCrateRoot(root: VirtualFile): Target?
    fun isCrateRoot(root: VirtualFile) = findTargetByCrateRoot(root) != null

    fun withStdlib(stdlib: StandardLibrary, cfgOptions: CfgOptions, rustcInfo: RustcInfo? = null): CargoWorkspace
    fun withDisabledFeatures(userDisabledFeatures: UserDisabledFeatures): CargoWorkspace
    val hasStandardLibrary: Boolean get() = packages.any { it.origin == STDLIB }

    @TestOnly
    fun withImplicitDependency(pkgToAdd: CargoWorkspaceData.Package): CargoWorkspace

    @TestOnly
    fun withEdition(edition: Edition): CargoWorkspace

    @TestOnly
    fun withCfgOptions(cfgOptions: CfgOptions): CargoWorkspace

    @TestOnly
    fun withCargoFeatures(features: Map<PackageFeature, List<FeatureDep>>): CargoWorkspace

    /** See docs for [CargoProjectsService] */
    interface Package : UserDataHolderEx {
        val contentRoot: VirtualFile?
        val rootDirectory: Path

        val id: String
        val name: String
        val normName: String get() = name.replace('-', '_')

        val version: String

        val source: String?
        val origin: PackageOrigin

        val targets: Collection<Target>
        val libTarget: Target? get() = targets.find { it.kind.isLib }
        val customBuildTarget: Target? get() = targets.find { it.kind == TargetKind.CustomBuild }
        val hasCustomBuildScript: Boolean get() = customBuildTarget != null

        val dependencies: Collection<Dependency>

        /**
         * Cfg options from the package custom build script (`build.rs`). `null` if there isn't build script
         * or the build script was not evaluated successfully or build script evaluation is disabled
         */
        val cfgOptions: CfgOptions?

        val features: Set<PackageFeature>

        val workspace: CargoWorkspace

        val edition: Edition

        val env: Map<String, String>

        val outDir: VirtualFile?

        val featureState: Map<FeatureName, FeatureState>

        val procMacroArtifact: CargoWorkspaceData.ProcMacroArtifact?

        fun findDependency(normName: String): Target? =
            if (this.normName == normName) libTarget else dependencies.find { it.name == normName }?.pkg?.libTarget
    }

    /** See docs for [CargoProjectsService] */
    interface Target {
        val name: String

        // target name must be a valid Rust identifier, so normalize it by mapping `-` to `_`
        // https://github.com/rust-lang/cargo/blob/ece4e963a3054cdd078a46449ef0270b88f74d45/src/cargo/core/manifest.rs#L299
        val normName: String get() = name.replace('-', '_')

        val kind: TargetKind

        val crateRoot: VirtualFile?

        val pkg: Package

        val edition: Edition

        val doctest: Boolean

        /** See [org.rust.cargo.toolchain.impl.CargoMetadata.Target.required_features] */
        val requiredFeatures: List<String>

        /** Complete `cfg` options of the target. Combines compiler options, package options and target options */
        val cfgOptions: CfgOptions
    }

    interface Dependency {
        val pkg: Package
        val name: String
        val cargoFeatureDependencyPackageName: String
        val depKinds: List<DepKindInfo>

        /**
         * Consider Cargo.toml:
         * ```
         * [dependencies.foo]
         * version = "*"
         * features = ["bar", "baz"]
         * ```
         * For dependency `foo`, features `bar` and `baz` are considered "required"
         */
        val requiredFeatures: Set<String>
    }

    data class DepKindInfo(
        val kind: DepKind,
        val target: String? = null
    )

    enum class DepKind(val cargoName: String?) {
        // For old Cargo versions prior to `1.41.0`
        Unclassified(null),

        Stdlib("stdlib?"),

        // [dependencies]
        Normal(null),

        // [dev-dependencies]
        Development("dev"),

        // [build-dependencies]
        Build("build")
    }

    sealed class TargetKind(val name: String) {
        class Lib(val kinds: EnumSet<LibKind>) : TargetKind("lib") {
            constructor(vararg kinds: LibKind) : this(EnumSet.copyOf(kinds.asList()))
        }

        object Bin : TargetKind("bin")
        object Test : TargetKind("test")
        object ExampleBin : TargetKind("example")
        class ExampleLib(val kinds: EnumSet<LibKind>) : TargetKind("example")
        object Bench : TargetKind("bench")
        object CustomBuild : TargetKind("custom-build")
        object Unknown : TargetKind("unknown")

        val isLib: Boolean get() = this is Lib
        val isBin: Boolean get() = this == Bin
        val isExampleBin: Boolean get() = this == ExampleBin
        val isCustomBuild: Boolean get() = this == CustomBuild
        val isProcMacro: Boolean
            get() = this is Lib && this.kinds.contains(LibKind.PROC_MACRO)
    }

    enum class LibKind {
        LIB, DYLIB, STATICLIB, CDYLIB, RLIB, PROC_MACRO, UNKNOWN
    }

    enum class Edition(val presentation: String) {
        EDITION_2015("2015"),
        EDITION_2018("2018"),
        EDITION_2021("2021");

        companion object {
            val DEFAULT: Edition = EDITION_2018
        }
    }

    companion object {
        fun deserialize(
            manifestPath: Path,
            data: CargoWorkspaceData,
            cfgOptions: CfgOptions = CfgOptions.DEFAULT,
            cargoConfig: CargoConfig = CargoConfig.DEFAULT,
        ): CargoWorkspace =
            WorkspaceImpl.deserialize(manifestPath, data, cfgOptions, cargoConfig)
    }
}


private class WorkspaceImpl(
    override val manifestPath: Path,
    val workspaceRootUrl: String?,
    packagesData: Collection<CargoWorkspaceData.Package>,
    override val cfgOptions: CfgOptions,
    override val cargoConfig: CargoConfig,
    val featuresState: Map<PackageRoot, Map<FeatureName, FeatureState>>
) : CargoWorkspace {

    override val workspaceRoot: VirtualFile? by CachedVirtualFile(workspaceRootUrl)
    override val packages: List<PackageImpl> = packagesData.map { pkg ->
        PackageImpl(
            this,
            pkg.id,
            pkg.contentRootUrl,
            pkg.name,
            pkg.version,
            pkg.targets,
            pkg.source,
            pkg.origin,
            pkg.edition,
            pkg.cfgOptions,
            pkg.features,
            pkg.enabledFeatures,
            pkg.env,
            pkg.outDirUrl,
            pkg.procMacroArtifact,
        )
    }

    override val featureGraph: FeatureGraph by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val wrappedFeatures = hashMapOf<PackageFeature, List<PackageFeature>>()

        for (pkg in packages) {
            val pkgFeatures = pkg.rawFeatures
            for (packageFeature in pkg.features) {
                if (packageFeature in wrappedFeatures) continue
                val deps = pkgFeatures[packageFeature.name] ?: continue

                val wrappedDeps = deps.flatMap { featureDep ->
                    when {
                        featureDep.startsWith("dep:") -> emptyList()
                        featureDep in pkgFeatures -> listOf(PackageFeature(pkg, featureDep))
                        "/" in featureDep -> {
                            val (firstSegment, name) = featureDep.split('/', limit = 2)
                            val optional = firstSegment.endsWith("?")
                            val depName = firstSegment.removeSuffix("?")

                            val dep = pkg.dependencies.find { it.cargoFeatureDependencyPackageName == depName }
                                ?: return@flatMap emptyList()

                            if (name in dep.pkg.rawFeatures) {
                                if (!optional && dep.isOptional) {
                                    listOf(PackageFeature(pkg, dep.cargoFeatureDependencyPackageName), PackageFeature(dep.pkg, name))
                                } else {
                                    listOf(PackageFeature(dep.pkg, name))
                                }
                            } else {
                                emptyList()
                            }
                        }
                        else -> emptyList()
                    }
                }
                wrappedFeatures[packageFeature] = wrappedDeps
            }
        }
        FeatureGraph.buildFor(wrappedFeatures)
    }

    val targetByCrateRootUrl = packages.flatMap { it.targets }.associateBy { it.crateRootUrl }

    override fun findTargetByCrateRoot(root: VirtualFile): CargoWorkspace.Target? =
        root.applyWithSymlink { targetByCrateRootUrl[it.url] }


    override fun withStdlib(stdlib: StandardLibrary, cfgOptions: CfgOptions, rustcInfo: RustcInfo?): CargoWorkspace {
        // This is a bit trickier than it seems required.
        // The problem is that workspace packages and targets have backlinks
        // so we have to rebuild the whole workspace from scratch instead of
        // *just* adding in the stdlib.

        val (newPackagesData, @Suppress("NAME_SHADOWING") stdlib) = if (!stdlib.isPartOfCargoProject) {
            Pair(
                packages.map { it.asPackageData() } + stdlib.asPackageData(rustcInfo),
                stdlib
            )
        } else {
            // In the case of https://github.com/rust-lang/rust project, stdlib
            // is already a part of the project, so no need to add extra packages.
            val oldPackagesData = packages.map { it.asPackageData() }
            val stdCratePackageRoots = stdlib.crates.mapToSet { it.contentRootUrl }
            val (stdPackagesData, otherPackagesData) = oldPackagesData.partition { it.contentRootUrl in stdCratePackageRoots }
            val stdPackagesByPackageRoot = stdPackagesData.associateBy { it.contentRootUrl }
            val pkgIdMapping = stdlib.crates.associate {
                it.id to (stdPackagesByPackageRoot[it.contentRootUrl]?.id ?: it.id)
            }
            val newStdlibCrates = stdlib.crates.map { it.copy(id = pkgIdMapping.getValue(it.id)) }
            val newStdlibDependencies = stdlib.workspaceData.dependencies.map { (oldId, dependency) ->
                val newDependencies = dependency.mapToSet { it.copy(id = pkgIdMapping.getValue(it.id)) }
                pkgIdMapping.getValue(oldId) to newDependencies
            }.toMap()

            Pair(
                otherPackagesData + stdPackagesData.map { it.copy(origin = STDLIB) },
                stdlib.copy(
                    workspaceData = stdlib.workspaceData.copy(
                        packages = newStdlibCrates,
                        dependencies = newStdlibDependencies
                    )
                )
            )
        }

        val stdAll = stdlib.crates.associateBy { it.id }
        val stdInternalDeps = stdlib.crates.filter { it.origin == STDLIB_DEPENDENCY }.mapToSet { it.id }

        val result = WorkspaceImpl(
            manifestPath,
            workspaceRootUrl,
            newPackagesData,
            cfgOptions,
            cargoConfig,
            featuresState
        )

        run {
            val oldIdToPackage = packages.associateBy { it.id }
            val newIdToPackage = result.packages.associateBy { it.id }
            val stdlibDependencies = result.packages.filter { it.origin == STDLIB }
                .map { DependencyImpl(it, depKinds = listOf(CargoWorkspace.DepKindInfo(CargoWorkspace.DepKind.Stdlib))) }
            newIdToPackage.forEach { (id, pkg) ->
                val stdCrate = stdAll[id]
                if (stdCrate == null) {
                    pkg.dependencies.addAll(oldIdToPackage[id]?.dependencies.orEmpty().mapNotNull { dep ->
                        val dependencyPackage = newIdToPackage[dep.pkg.id] ?: return@mapNotNull null
                        dep.withPackage(dependencyPackage)
                    })
                    val explicitDeps = pkg.dependencies.map { it.name }.toSet()
                    pkg.dependencies.addAll(stdlibDependencies.filter { it.name !in explicitDeps && it.pkg.id !in stdInternalDeps })
                } else {
                    // `pkg` is a crate from stdlib
                    pkg.addDependencies(stdlib.workspaceData, newIdToPackage)
                }
            }
        }

        return result
    }

    private fun withDependenciesOf(other: WorkspaceImpl): CargoWorkspace {
        val otherIdToPackage = other.packages.associateBy { it.id }
        val thisIdToPackage = packages.associateBy { it.id }
        thisIdToPackage.forEach { (id, pkg) ->
            pkg.dependencies.addAll(otherIdToPackage[id]?.dependencies.orEmpty().mapNotNull { dep ->
                val dependencyPackage = thisIdToPackage[dep.pkg.id] ?: return@mapNotNull null
                dep.withPackage(dependencyPackage)
            })
        }
        return this
    }

    override fun withDisabledFeatures(userDisabledFeatures: UserDisabledFeatures): CargoWorkspace {
        val featuresState = inferFeatureState(userDisabledFeatures).associateByPackageRoot()

        return WorkspaceImpl(
            manifestPath,
            workspaceRootUrl,
            packages.map { it.asPackageData() },
            cfgOptions,
            cargoConfig,
            featuresState
        ).withDependenciesOf(this)
    }

    /**
     * Infers a state of each Cargo feature of each package in the workspace (including dependencies!).
     *
     * Initial state: all [DEPENDENCY] packages features are disabled, all [WORKSPACE] packages features are enabled,
     * excluding [userDisabledFeatures] features.
     * Then we enable [DEPENDENCY] packages features transitively based on the initial state and features dependencies
     */
    private fun inferFeatureState(userDisabledFeatures: UserDisabledFeatures): Map<PackageFeature, FeatureState> {
        // Calculate features that should be enabled in the workspace, all by default (if `userDisabledFeatures` is empty)
        val workspaceFeatureState = featureGraph.apply(defaultState = FeatureState.Enabled) {
            disableAll(userDisabledFeatures.getDisabledFeatures(packages))
        }

        return featureGraph.apply(defaultState = FeatureState.Disabled) {
            for (pkg in packages) {
                // Enable remained workspace features (transitively)
                if (pkg.origin == WORKSPACE || pkg.origin == STDLIB) {
                    for (feature in pkg.features) {
                        if (workspaceFeatureState[feature] == FeatureState.Enabled) {
                            enable(feature)
                        }
                    }
                }

                // Also, enable features specified in dependencies configuration. Consider `Cargo.toml`:
                // ```
                // [dependency]
                // foo = { version = "*", feature = ["bar", "baz"] }
                //                                 #^ enable these features
                // ```
                // Here features `bar` and `baz` in package `foo` should be enabled, but only if the
                // package `foo` is not a workspace member. Otherwise its features are controlled by
                // a user (and enabled by default)
                for (dependency in pkg.dependencies) {
                    if (dependency.pkg.origin == WORKSPACE || dependency.pkg.origin == STDLIB) continue
                    if (dependency.areDefaultFeaturesEnabled) {
                        enable(PackageFeature(dependency.pkg, "default"))
                    }
                    enableAll(dependency.requiredFeatures.map { PackageFeature(dependency.pkg, it) })
                }
            }
        }
    }

    @TestOnly
    override fun withImplicitDependency(pkgToAdd: CargoWorkspaceData.Package): CargoWorkspace {
        val newPackagesData = packages.map { it.asPackageData() } + listOf(pkgToAdd)

        val result = WorkspaceImpl(
            manifestPath,
            workspaceRootUrl,
            newPackagesData,
            cfgOptions,
            cargoConfig,
            featuresState
        )

        run {
            @Suppress("DuplicatedCode")
            val oldIdToPackage = packages.associateBy { it.id }
            val newIdToPackage = result.packages.associateBy { it.id }
            val stdlibDependencies = result.packages.filter { it.origin == STDLIB }
                .map { DependencyImpl(it, depKinds = listOf(CargoWorkspace.DepKindInfo(CargoWorkspace.DepKind.Stdlib))) }
            val pkgToAddDependency = DependencyImpl(
                result.packages.find { it.id == pkgToAdd.id }!!,
                depKinds = listOf(CargoWorkspace.DepKindInfo(CargoWorkspace.DepKind.Stdlib))
            )
            newIdToPackage.forEach { (id, pkg) ->
                if (id == pkgToAdd.id) {
                    pkg.dependencies.addAll(stdlibDependencies)
                } else {
                    pkg.dependencies.addAll(oldIdToPackage[id]?.dependencies.orEmpty().mapNotNull { dep ->
                        val dependencyPackage = newIdToPackage[dep.pkg.id] ?: return@mapNotNull null
                        dep.withPackage(dependencyPackage)
                    })
                    pkg.dependencies.add(pkgToAddDependency)
                }
            }
        }

        return result
    }

    @TestOnly
    override fun withEdition(edition: CargoWorkspace.Edition): CargoWorkspace = WorkspaceImpl(
        manifestPath,
        workspaceRootUrl,
        packages.map { pkg ->
            val packageEdition = if (pkg.origin == STDLIB || pkg.origin == STDLIB_DEPENDENCY) pkg.edition else edition
            pkg.asPackageData(packageEdition)
        },
        cfgOptions,
        cargoConfig,
        featuresState
    ).withDependenciesOf(this)

    @TestOnly
    override fun withCfgOptions(cfgOptions: CfgOptions): CargoWorkspace = WorkspaceImpl(
        manifestPath,
        workspaceRootUrl,
        packages.map { it.asPackageData() },
        cfgOptions,
        cargoConfig,
        featuresState
    ).withDependenciesOf(this)

    @TestOnly
    override fun withCargoFeatures(features: Map<PackageFeature, List<FeatureDep>>): CargoWorkspace {
        val packageToFeatures = features.entries
            .groupBy { it.key.pkg }
            .mapValues { (_, v) -> v.associate { it.key.name to it.value } }
        return WorkspaceImpl(
            manifestPath,
            workspaceRootUrl,
            packages.map { it.asPackageData().copy(features = packageToFeatures[it].orEmpty(), enabledFeatures = packageToFeatures[it].orEmpty().keys) },
            cfgOptions,
            cargoConfig,
            featuresState
        ).withDependenciesOf(this).withDisabledFeatures(UserDisabledFeatures.EMPTY)
    }

    override fun toString(): String {
        val pkgs = packages.joinToString(separator = "") { "    $it,\n" }
        return "Workspace(packages=[\n$pkgs])"
    }

    companion object {
        fun deserialize(
            manifestPath: Path,
            data: CargoWorkspaceData,
            cfgOptions: CfgOptions,
            cargoConfig: CargoConfig,
        ): WorkspaceImpl {
            // Packages form mostly a DAG. "Why mostly?", you say.
            // Well, a dev-dependency `X` of package `P` can depend on the `P` itself.
            // This is ok, because cargo can compile `P` (without `X`, because dev-deps
            // are used only for tests), then `X`, and then `P`s tests. So we need to
            // handle cycles here.

            val result = WorkspaceImpl(
                manifestPath,
                data.workspaceRootUrl,
                data.packages,
                cfgOptions,
                cargoConfig,
                emptyMap()
            )

            // Fill package dependencies
            run {
                val idToPackage = result.packages.associateBy { it.id }
                idToPackage.forEach { (_, pkg) -> pkg.addDependencies(data, idToPackage) }
            }

            return result
        }
    }
}


private class PackageImpl(
    override val workspace: WorkspaceImpl,
    override val id: PackageId,
    // Note: In tests, we use in-memory file system,
    // so we can't use `Path` here.
    val contentRootUrl: String,
    override val name: String,
    override val version: String,
    targetsData: Collection<CargoWorkspaceData.Target>,
    override val source: String?,
    override var origin: PackageOrigin,
    override val edition: CargoWorkspace.Edition,
    override val cfgOptions: CfgOptions?,
    /** See [org.rust.cargo.toolchain.impl.CargoMetadata.Package.features] */
    val rawFeatures: Map<FeatureName, List<FeatureDep>>,
    val cargoEnabledFeatures: Set<FeatureName>,
    override val env: Map<String, String>,
    val outDirUrl: String?,
    override val procMacroArtifact: CargoWorkspaceData.ProcMacroArtifact?,
) : UserDataHolderBase(), CargoWorkspace.Package {
    override val targets = targetsData.map {
        TargetImpl(
            this,
            crateRootUrl = it.crateRootUrl,
            name = it.name,
            kind = it.kind,
            edition = it.edition,
            doctest = it.doctest,
            requiredFeatures = it.requiredFeatures
        )
    }

    override val contentRoot: VirtualFile? by CachedVirtualFile(contentRootUrl)

    override val rootDirectory: Path
        get() = Paths.get(VirtualFileManager.extractPath(contentRootUrl))

    override val dependencies: MutableList<DependencyImpl> = ArrayList()

    override val outDir: VirtualFile? by CachedVirtualFile(outDirUrl)

    override val features: Set<PackageFeature> = rawFeatures.keys.mapToSet { PackageFeature(this, it) }

    override val featureState: Map<FeatureName, FeatureState>
        get() = workspace.featuresState[rootDirectory] ?: emptyMap()

    override fun toString() = "Package(name='$name', contentRootUrl='$contentRootUrl', outDirUrl='$outDirUrl')"
}

private fun PackageImpl.addDependencies(workspaceData: CargoWorkspaceData, packagesMap: Map<PackageId, PackageImpl>) {
    val pkgDeps = workspaceData.dependencies[id].orEmpty()
    val pkgRawDeps = workspaceData.rawDependencies[id].orEmpty()
    dependencies += pkgDeps.mapNotNull { dep ->
        val dependencyPackage = packagesMap[dep.id] ?: return@mapNotNull null

        val depTargetName = dependencyPackage.libTarget?.normName ?: dependencyPackage.normName
        val depName = dep.name ?: depTargetName
        val rename = if (depName != depTargetName) depName else null

        // There can be multiple appropriate raw dependencies because a dependency can be mentioned
        // in `Cargo.toml` in different sections, e.g. [dev-dependencies] and [build-dependencies]
        val rawDeps = pkgRawDeps.filter { rawDep ->
            rawDep.name == dependencyPackage.name && rawDep.rename?.replace('-', '_') == rename && dep.depKinds.any {
                it.kind == CargoWorkspace.DepKind.Unclassified ||
                    it.target == rawDep.target && it.kind.cargoName == rawDep.kind
            }
        }

        DependencyImpl(
            dependencyPackage,
            depName,
            dep.depKinds,
            rawDeps.any { it.optional },
            rawDeps.any { it.uses_default_features },
            rawDeps.flatMap { it.features }.toSet(),
            rawDeps.firstOrNull()?.rename ?: dependencyPackage.name
        )
    }
}

private class TargetImpl(
    override val pkg: PackageImpl,
    val crateRootUrl: String,
    override val name: String,
    override val kind: CargoWorkspace.TargetKind,
    override val edition: CargoWorkspace.Edition,
    override val doctest: Boolean,
    override val requiredFeatures: List<FeatureName>
) : CargoWorkspace.Target {

    override val crateRoot: VirtualFile? by CachedVirtualFile(crateRootUrl)

    override val cfgOptions: CfgOptions = pkg.workspace.cfgOptions + (pkg.cfgOptions ?: CfgOptions.EMPTY) +
        // https://doc.rust-lang.org/reference/conditional-compilation.html#proc_macro
        if (kind.isProcMacro) CfgOptions(emptyMap(), setOf("proc_macro")) else CfgOptions.EMPTY

    override fun toString(): String = "Target(name='$name', kind=$kind, crateRootUrl='$crateRootUrl')"
}

private class DependencyImpl(
    override val pkg: PackageImpl,
    override val name: String = pkg.libTarget?.normName ?: pkg.normName,
    override val depKinds: List<CargoWorkspace.DepKindInfo>,
    val isOptional: Boolean = false,
    val areDefaultFeaturesEnabled: Boolean = true,
    override val requiredFeatures: Set<String> = emptySet(),
    override val cargoFeatureDependencyPackageName: String = if (name == pkg.libTarget?.normName) pkg.name else name
) : CargoWorkspace.Dependency {

    fun withPackage(newPkg: PackageImpl): DependencyImpl =
        DependencyImpl(
            newPkg,
            name,
            depKinds,
            isOptional,
            areDefaultFeaturesEnabled,
            requiredFeatures,
            cargoFeatureDependencyPackageName
        )

    override fun toString(): String = name
}

/**
 * A way to add additional (indexable) source roots for a package.
 * These hacks are needed for the stdlib that has a weird source structure.
 */
fun CargoWorkspace.Package.additionalRoots(): List<VirtualFile> {
    return if (origin == STDLIB) {
        when (name) {
            STD -> listOfNotNull(contentRoot?.parent?.findFileByRelativePath("backtrace"))
            CORE -> contentRoot?.parent?.let {
                listOfNotNull(
                    it.findFileByRelativePath("stdarch/crates/core_arch"),
                    it.findFileByRelativePath("stdarch/crates/std_detect"),
                    it.findFileByRelativePath("portable-simd/crates/core_simd"),
                    it.findFileByRelativePath("portable-simd/crates/std_float"),
                )
            } ?: emptyList()
            else -> emptyList()
        }
    } else {
        emptyList()
    }
}

private fun PackageImpl.asPackageData(edition: CargoWorkspace.Edition? = null): CargoWorkspaceData.Package =
    CargoWorkspaceData.Package(
        id = id,
        contentRootUrl = contentRootUrl,
        name = name,
        version = version,
        targets = targets.map {
            CargoWorkspaceData.Target(
                crateRootUrl = it.crateRootUrl,
                name = it.name,
                kind = it.kind,
                edition = edition ?: it.edition,
                doctest = it.doctest,
                requiredFeatures = it.requiredFeatures
            )
        },
        source = source,
        origin = origin,
        edition = edition ?: this.edition,
        features = rawFeatures,
        enabledFeatures = cargoEnabledFeatures,
        cfgOptions = cfgOptions,
        env = env,
        outDirUrl = outDirUrl,
        procMacroArtifact = procMacroArtifact
    )
