/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapiext.isUnitTestMode
import com.intellij.util.text.SemVer
import org.jetbrains.annotations.TestOnly
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.impl.UserDisabledFeatures
import org.rust.cargo.project.workspace.PackageOrigin.*
import org.rust.cargo.util.AutoInjectedCrates.CORE
import org.rust.cargo.util.AutoInjectedCrates.STD
import org.rust.cargo.util.StdLibType
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

    val workspaceRootPath: Path?

    val cfgOptions: CfgOptions

    /**
     * Flatten list of packages including workspace members, dependencies, transitive dependencies
     * and stdlib. Use `packages.filter { it.origin == PackageOrigin.WORKSPACE }` to
     * obtain workspace members.
     */
    val packages: Collection<Package>

    val featureGraph: FeatureGraph

    fun findPackage(name: String): Package? = packages.find { it.name == name || it.normName == name }

    fun findTargetByCrateRoot(root: VirtualFile): Target?
    fun isCrateRoot(root: VirtualFile) = findTargetByCrateRoot(root) != null

    fun withStdlib(stdlib: StandardLibrary, cfgOptions: CfgOptions, rustcInfo: RustcInfo? = null): CargoWorkspace
    fun withDisabledFeatures(userDisabledFeatures: UserDisabledFeatures): CargoWorkspace
    val hasStandardLibrary: Boolean get() = packages.any { it.origin == STDLIB }

    @TestOnly
    fun withEdition(edition: Edition): CargoWorkspace

    @TestOnly
    fun withCfgOptions(cfgOptions: CfgOptions): CargoWorkspace

    /** See docs for [CargoProjectsService] */
    interface Package {
        val contentRoot: VirtualFile?
        val rootDirectory: Path

        val name: String
        val normName: String get() = name.replace('-', '_')

        val version: String

        val source: String?
        val origin: PackageOrigin

        val targets: Collection<Target>
        val libTarget: Target? get() = targets.find { it.kind.isLib }
        val customBuildTarget: Target? get() = targets.find { it.kind == TargetKind.CustomBuild }

        val dependencies: Collection<Dependency>

        val cfgOptions: CfgOptions

        val features: Set<PackageFeature>

        val workspace: CargoWorkspace

        val edition: Edition

        val env: Map<String, String>

        val outDir: VirtualFile?

        val featureState: Map<FeatureName, FeatureState>

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
    }

    interface Dependency {
        val pkg: Package
        val name: String
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
        EDITION_2015("2015"), EDITION_2018("2018")
    }

    companion object {
        fun deserialize(manifestPath: Path, data: CargoWorkspaceData, cfgOptions: CfgOptions): CargoWorkspace =
            WorkspaceImpl.deserialize(manifestPath, data, cfgOptions)
    }
}


private class WorkspaceImpl(
    override val manifestPath: Path,
    override val workspaceRootPath: Path?,
    packagesData: Collection<CargoWorkspaceData.Package>,
    override val cfgOptions: CfgOptions,
    val featuresState: Map<PackageRoot, Map<FeatureName, FeatureState>>
) : CargoWorkspace {
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
            pkg.outDirUrl
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
                        featureDep in pkgFeatures -> listOf(PackageFeature(pkg, featureDep))
                        "/" in featureDep -> {
                            val (crateName, name) = featureDep.split('/', limit = 2)

                            // Features are linked by a `Package` name, not by dependency name
                            val dep = pkg.dependencies.find { it.pkg.name == crateName }
                                ?: return@flatMap emptyList()

                            if (name in dep.pkg.rawFeatures) {
                                if (dep.isOptional) {
                                    listOf(PackageFeature(pkg, dep.pkg.name), PackageFeature(dep.pkg, name))
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

        val (newPackagesData, stdCrates) = if (!stdlib.isPartOfCargoProject) {
            Pair(
                packages.map { it.asPackageData() } + stdlib.crates.map { it.asPackageData(rustcInfo) },
                stdlib.crates
            )
        } else {
            // In the case of https://github.com/rust-lang/rust project, stdlib
            // is already a part of the project, so no need to add extra packages.
            val oldPackagesData = packages.map { it.asPackageData() }
            val stdCratePackageRoots = stdlib.crates.mapToSet { it.packageRootUrl }
            val (stdPackagesData, otherPackagesData) = oldPackagesData.partition { it.contentRootUrl in stdCratePackageRoots }
            val stdPackagesByPackageRoot = stdPackagesData.associateBy { it.contentRootUrl }
            Pair(
                otherPackagesData + stdPackagesData.map { it.copy(origin = PackageOrigin.STDLIB) },
                stdlib.crates.map { it.copy(id = stdPackagesByPackageRoot[it.packageRootUrl]?.id ?: it.id) }
            )
        }

        val stdAll = stdCrates.associateBy { it.id }
        val stdInternalDeps = stdCrates.filter { it.type == StdLibType.DEPENDENCY }.map { it.id }.toSet()

        val result = WorkspaceImpl(
            manifestPath,
            workspaceRootPath,
            newPackagesData,
            cfgOptions,
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
                    val stdCrateDeps = stdCrate.dependencies.mapNotNull { stdDep ->
                        stdlibDependencies.find { it.name == stdDep }
                    }
                    pkg.dependencies.addAll(stdCrateDeps)
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
        checkFeaturesInference()
        val featuresState = inferFeatureState(userDisabledFeatures).associateByPackageRoot()

        return WorkspaceImpl(
            manifestPath,
            workspaceRootPath,
            packages.map { it.asPackageData() },
            cfgOptions,
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

    /** A kind of test for [inferFeatureState]: check that our features inference works the same way as Cargo's */
    private fun checkFeaturesInference() {
        if (!isUnitTestMode) return
        val enabledByCargo = packages.flatMap { pkg ->
            pkg.cargoEnabledFeatures.map { PackageFeature(pkg, it) }
        }
        for ((feature, state) in inferFeatureState(UserDisabledFeatures.EMPTY)) {
            if (feature in enabledByCargo != state.isEnabled) {
                error("Feature ${feature.name} in package ${feature.pkg.name} should be ${!state}, but it is $state")
            }
        }
    }

    @TestOnly
    override fun withEdition(edition: CargoWorkspace.Edition): CargoWorkspace = WorkspaceImpl(
        manifestPath,
        workspaceRootPath,
        packages.map { pkg ->
            // Currently, stdlib doesn't use 2018 edition
            val packageEdition = if (pkg.origin == STDLIB) pkg.edition else edition
            pkg.asPackageData(packageEdition)
        },
        cfgOptions,
        featuresState
    ).withDependenciesOf(this)

    @TestOnly
    override fun withCfgOptions(cfgOptions: CfgOptions): CargoWorkspace = WorkspaceImpl(
        manifestPath,
        workspaceRootPath,
        packages.map { it.asPackageData() },
        cfgOptions,
        featuresState
    ).withDependenciesOf(this)

    override fun toString(): String {
        val pkgs = packages.joinToString(separator = "") { "    $it,\n" }
        return "Workspace(packages=[\n$pkgs])"
    }

    companion object {
        fun deserialize(manifestPath: Path, data: CargoWorkspaceData, cfgOptions: CfgOptions): WorkspaceImpl {
            // Packages form mostly a DAG. "Why mostly?", you say.
            // Well, a dev-dependency `X` of package `P` can depend on the `P` itself.
            // This is ok, because cargo can compile `P` (without `X`, because dev-deps
            // are used only for tests), then `X`, and then `P`s tests. So we need to
            // handle cycles here.

            val workspaceRootPath = data.workspaceRoot?.let { Paths.get(it) }
            val result = WorkspaceImpl(manifestPath, workspaceRootPath, data.packages, cfgOptions, emptyMap())

            // Fill package dependencies
            run {
                val idToPackage = result.packages.associateBy { it.id }
                idToPackage.forEach { (id, pkg) ->
                    val pkgDeps = data.dependencies[id].orEmpty()
                    val pkgRawDeps = data.rawDependencies[id].orEmpty()
                    pkg.dependencies += pkgDeps.mapNotNull { dep ->
                        val dependencyPackage = idToPackage[dep.id] ?: return@mapNotNull null

                        // There can be multiple appropriate raw dependencies because a dependency can be mentioned
                        // in `Cargo.toml` in different sections, e.g. [dev-dependencies] and [build-dependencies]
                        val rawDeps = pkgRawDeps.filter { rawDep ->
                            rawDep.name == dependencyPackage.name && dep.depKinds.any {
                                it.kind == CargoWorkspace.DepKind.Unclassified ||
                                    it.target == rawDep.target && it.kind.cargoName == rawDep.kind
                            }
                        }

                        val depName = dep.name ?: (dependencyPackage.libTarget?.normName ?: dependencyPackage.normName)

                        DependencyImpl(
                            dependencyPackage,
                            depName,
                            dep.depKinds,
                            rawDeps.any { it.optional },
                            rawDeps.any { it.uses_default_features },
                            rawDeps.flatMap { it.features }.toSet()
                        )
                    }
                }
            }

            return result
        }
    }
}


private class PackageImpl(
    override val workspace: WorkspaceImpl,
    val id: PackageId,
    // Note: In tests, we use in-memory file system,
    // so we can't use `Path` here.
    val contentRootUrl: String,
    override val name: String,
    override val version: String,
    targetsData: Collection<CargoWorkspaceData.Target>,
    override val source: String?,
    override var origin: PackageOrigin,
    override val edition: CargoWorkspace.Edition,
    override val cfgOptions: CfgOptions,
    /** See [org.rust.cargo.toolchain.impl.CargoMetadata.Package.features] */
    val rawFeatures: Map<FeatureName, List<FeatureDep>>,
    val cargoEnabledFeatures: Set<FeatureName>,
    override val env: Map<String, String>,
    val outDirUrl: String?
) : CargoWorkspace.Package {
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

    override fun toString(): String = "Target(name='$name', kind=$kind, crateRootUrl='$crateRootUrl')"
}

private class DependencyImpl(
    override val pkg: PackageImpl,
    override val name: String = pkg.libTarget?.normName ?: pkg.normName,
    override val depKinds: List<CargoWorkspace.DepKindInfo>,
    val isOptional: Boolean = false,
    val areDefaultFeaturesEnabled: Boolean = true,
    override val requiredFeatures: Set<String> = emptySet()
) : CargoWorkspace.Dependency {
    fun withPackage(newPkg: PackageImpl): DependencyImpl =
        DependencyImpl(newPkg, name, depKinds, isOptional, areDefaultFeaturesEnabled, requiredFeatures)

    override fun toString(): String = name
}

/**
 * A way to add additional (indexable) source roots for a package.
 * These hacks are needed for the stdlib that has a weird source structure.
 */
fun CargoWorkspace.Package.additionalRoots(): List<VirtualFile> {
    return if (origin == PackageOrigin.STDLIB) {
        when (name) {
            STD -> listOfNotNull(contentRoot?.parent?.findFileByRelativePath("backtrace"))
            CORE -> contentRoot?.parent?.let {
                listOfNotNull(
                    it.findFileByRelativePath("stdarch/crates/core_arch"),
                    it.findFileByRelativePath("stdarch/crates/std_detect")
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
        outDirUrl = outDirUrl
    )

private fun StandardLibrary.StdCrate.asPackageData(rustcInfo: RustcInfo?): CargoWorkspaceData.Package {
    val firstVersionWithEdition2018 = when (name) {
        CORE -> RUST_1_36
        STD -> RUST_1_35
        else -> RUST_1_34
    }

    val currentRustcVersion = rustcInfo?.version?.semver
    val edition = if (currentRustcVersion == null || currentRustcVersion < firstVersionWithEdition2018) {
        CargoWorkspace.Edition.EDITION_2015
    } else {
        CargoWorkspace.Edition.EDITION_2018
    }

    return CargoWorkspaceData.Package(
        id = id,
        contentRootUrl = packageRootUrl,
        name = name,
        version = "",
        targets = listOf(CargoWorkspaceData.Target(
            crateRootUrl = crateRootUrl,
            name = name,
            kind = CargoWorkspace.TargetKind.Lib(CargoWorkspace.LibKind.LIB),
            edition = edition,
            doctest = true,
            requiredFeatures = emptyList()
        )),
        source = null,
        origin = STDLIB,
        edition = edition,
        features = emptyMap(),
        enabledFeatures = emptySet(),
        cfgOptions = CfgOptions.EMPTY,
        env = emptyMap(),
        outDirUrl = null
    )
}

private val RUST_1_34: SemVer = SemVer.parseFromText("1.34.0")!!
private val RUST_1_35: SemVer = SemVer.parseFromText("1.35.0")!!
private val RUST_1_36: SemVer = SemVer.parseFromText("1.36.0")!!
