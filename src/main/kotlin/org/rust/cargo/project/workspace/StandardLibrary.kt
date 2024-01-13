/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace

import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.delete
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.cargo.CargoConfig
import org.rust.cargo.CargoConstants
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.ProcessProgressListener
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.RsToolchainBase.Companion.RUSTC_BOOTSTRAP
import org.rust.cargo.toolchain.impl.CargoMetadata
import org.rust.cargo.toolchain.impl.CargoMetadataException
import org.rust.cargo.toolchain.impl.RustcVersion
import org.rust.cargo.toolchain.tools.Cargo
import org.rust.cargo.toolchain.tools.cargo
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.cargo.util.StdLibType
import org.rust.cargo.util.parseSemVer
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.RsPathManager
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.pathAsPath
import org.rust.stdext.HashCode
import org.rust.stdext.toPath
import org.rust.stdext.unwrapOrElse
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.exists

data class StandardLibrary(
    val workspaceData: CargoWorkspaceData,
    val isHardcoded: Boolean,
    val isPartOfCargoProject: Boolean = false
) {
    val crates: List<CargoWorkspaceData.Package> get() = workspaceData.packages

    companion object {
        private val LOG: Logger = logger<StandardLibrary>()

        private val SRC_ROOTS: List<String> = listOf("library", "src")
        private val LIB_PATHS: List<String> = listOf("src/lib.rs", "lib.rs")

        fun fromPath(
            project: Project,
            path: String,
            rustcInfo: RustcInfo?,
            cargoConfig: CargoConfig = CargoConfig.DEFAULT,
            isPartOfCargoProject: Boolean = false,
            listener: ProcessProgressListener? = null
        ): StandardLibrary? = LocalFileSystem.getInstance().findFileByPath(path)?.let {
            fromFile(project, it, rustcInfo, cargoConfig, isPartOfCargoProject, listener)
        }

        fun fromFile(
            project: Project,
            sources: VirtualFile,
            rustcInfo: RustcInfo?,
            cargoConfig: CargoConfig = CargoConfig.DEFAULT,
            isPartOfCargoProject: Boolean = false,
            listener: ProcessProgressListener? = null,
            useBsp: Boolean = false
        ): StandardLibrary? {
            val srcDir = findSrcDir(sources) ?: return null

            if (useBsp) {
                return fetchHardcodedStdlib(srcDir)
            }

            fun warn(message: @Nls String) {
                LOG.warn(message)
                listener?.warning(message, "")
            }

            val stdlib = if (isFeatureEnabled(RsExperiments.FETCH_ACTUAL_STDLIB_METADATA) && !isPartOfCargoProject) {
                val rustcVersion = rustcInfo?.version
                val semverVersion = rustcVersion?.semver
                if (semverVersion == null) {
                    warn(RsBundle.message("toolchain.version.is.unknown.hardcoded.stdlib.structure.will.be.used"))
                    fetchHardcodedStdlib(srcDir)
                } else {
                    val buildTargets = cargoConfig.buildTargets.ifEmpty { listOfNotNull(rustcVersion.host) }
                    val result = fetchActualStdlib(project, srcDir, rustcVersion, buildTargets, rustcInfo.rustupActiveToolchain, listener)
                    if (result == null) {
                        warn(RsBundle.message("fetching.actual.stdlib.info.failed.hardcoded.stdlib.structure.will.be.used"))
                    }
                    result ?: fetchHardcodedStdlib(srcDir)
                }
            } else {
                fetchHardcodedStdlib(srcDir)
            }

            return stdlib?.copy(isPartOfCargoProject = isPartOfCargoProject)
        }

        @VisibleForTesting
        fun findSrcDir(sources: VirtualFile): VirtualFile? {
            if (!sources.isDirectory) return null
            return if (sources.name in SRC_ROOTS) {
                sources
            } else {
                sources.findFirstFileByRelativePaths(SRC_ROOTS) ?: sources
            }
        }

        private fun fetchActualStdlib(
            project: Project,
            srcDir: VirtualFile,
            version: RustcVersion,
            buildTargets: List<String>,
            activeToolchain: String?,
            listener: ProcessProgressListener?,
            cleanVendorDir: Boolean = false
        ): StandardLibrary? {
            try {
                return StdlibDataFetcher.create(project, srcDir, version, buildTargets, activeToolchain, listener, cleanVendorDir)?.fetchStdlibData()
            } catch (e: Throwable) {
                if (isUnitTestMode) {
                    // Don't fail a test - we have some tests that check error recovery during stdlib fetching
                    LOG.warn(e)
                } else {
                    LOG.error(e)
                }
                if (!cleanVendorDir && e is CargoMetadataException) {
                    return fetchActualStdlib(project, srcDir, version, buildTargets, activeToolchain, listener, cleanVendorDir = true)
                }
            }
            return null
        }

        private fun fetchHardcodedStdlib(srcDir: VirtualFile): StandardLibrary? {
            val crates = mutableMapOf<PackageId, CargoWorkspaceData.Package>()

            for (libInfo in AutoInjectedCrates.stdlibCrates) {
                val packageSrcPaths = listOf(libInfo.name, "lib${libInfo.name}")
                val packageSrcDir = srcDir.findFirstFileByRelativePaths(packageSrcPaths)?.canonicalFile
                val libFile = packageSrcDir?.findFirstFileByRelativePaths(LIB_PATHS)
                if (packageSrcDir != null && libFile != null) {
                    val cratePkg = CargoWorkspaceData.Package(
                        id = libInfo.name.toStdlibId(),
                        contentRootUrl = packageSrcDir.url,
                        name = libInfo.name,
                        version = "",
                        targets = listOf(CargoWorkspaceData.Target(
                            crateRootUrl = libFile.url,
                            name = libInfo.name,
                            kind = CargoWorkspace.TargetKind.Lib(CargoWorkspace.LibKind.LIB),
                            edition = CargoWorkspace.Edition.EDITION_2015,
                            doctest = true,
                            requiredFeatures = emptyList()
                        )),
                        source = null,
                        origin = PackageOrigin.STDLIB,
                        edition = CargoWorkspace.Edition.EDITION_2015,
                        features = emptyMap(),
                        enabledFeatures = emptySet(),
                        cfgOptions = CfgOptions.EMPTY,
                        env = emptyMap(),
                        outDirUrl = null
                    )
                    crates[cratePkg.id] = cratePkg
                }
            }

            val dependencies = mutableMapOf<PackageId, MutableSet<CargoWorkspaceData.Dependency>>()
            val depKinds = listOf(CargoWorkspace.DepKindInfo(CargoWorkspace.DepKind.Stdlib))

            for (libInfo in AutoInjectedCrates.stdlibCrates) {
                val pkgId = libInfo.name.toStdlibId()
                if (crates[pkgId] == null) continue

                for (dependency in libInfo.dependencies) {
                    val dependencyId = dependency.toStdlibId()
                    if (crates[dependencyId] != null) {
                        dependencies.getOrPut(pkgId, ::mutableSetOf) += CargoWorkspaceData.Dependency(dependencyId, depKinds = depKinds)
                    }
                }
            }

            if (crates.isEmpty()) return null
            val data = CargoWorkspaceData(crates.values.toList(), dependencies, emptyMap())
            return StandardLibrary(workspaceData = data, isHardcoded = true)
        }
    }
}

class StdlibDataFetcher private constructor(
    private val project: Project,
    private val cargo: Cargo,
    private val srcDir: VirtualFile,
    private val version: RustcVersion,
    private val testPackageSrcDir: VirtualFile,
    private val stdlibDependenciesDir: Path,
    private val buildTargets: List<String>,
    private val activeToolchain: String?,
    private val listener: ProcessProgressListener?
) {
    private val workspaceMembers = mutableListOf<PackageId>()
    private val visitedPackages = mutableSetOf<PackageId>()
    private val allPackages = mutableListOf<CargoMetadata.Package>()
    private val allNodes = mutableListOf<CargoMetadata.ResolveNode>()

    fun fetchStdlibData(): StandardLibrary {
        // `test` package depends on all other stdlib packages from `AutoInjectedCrates` (at least on moment of writing)
        // so let's collect its metadata first to avoid redundant calls of `cargo metadata`
        testPackageSrcDir.collectPackageMetadata()
        // if there is a package that is not in dependencies of `test` package,
        // collect its metadata manually
        val rootStdlibCrates = AutoInjectedCrates.stdlibCrates.filter { it.type != StdLibType.DEPENDENCY }
        for (libInfo in rootStdlibCrates) {
            val packageSrcPaths = listOf(libInfo.name, "lib${libInfo.name}")
            val packageSrcDir = srcDir.findFirstFileByRelativePaths(packageSrcPaths)?.canonicalFile ?: continue

            val packageManifestPath = packageSrcDir.pathAsPath.resolve(CargoConstants.MANIFEST_FILE).toString()
            val pkg = allPackages.find { it.manifest_path == packageManifestPath }
            if (pkg == null) {
                packageSrcDir.collectPackageMetadata()
            } else {
                workspaceMembers += pkg.id
            }
        }

        val stdlibMetadataProject = CargoMetadata.Project(
            allPackages,
            CargoMetadata.Resolve(allNodes),
            1,
            workspaceMembers,
            srcDir.path
        )
        val stdlibWorkspaceData = CargoMetadata.clean(stdlibMetadataProject)
        val stdlibPackages = stdlibWorkspaceData.packages.map {
            val newOrigin = if (it.source == null) PackageOrigin.STDLIB else PackageOrigin.STDLIB_DEPENDENCY
            it.copy(origin = newOrigin)
        }
        return StandardLibrary(stdlibWorkspaceData.copy(packages = stdlibPackages), isHardcoded = false)
    }


    private fun String.remapPath(libName: String, version: String): String {
        val path = toPath()
        for (i in path.nameCount - 1 downTo 0) {
            val fileName = path.getName(i).fileName.toString()
            if (fileName.startsWith(libName) && fileName.endsWith(version)) {
                val subpath = path.subpath(i + 1, path.nameCount)
                return stdlibDependenciesDir.resolve(libName).resolve(subpath).toString()
            }
        }
        error("Failed to remap `$this`")
    }

    private fun CargoMetadata.Project.walk(id: PackageId, root: Boolean) {
        if (id in visitedPackages) return
        val stdlibId = id.toStdlibId()

        if (root) {
            workspaceMembers += stdlibId
        }

        visitedPackages += id

        val pkg = packages.first { it.id == id }

        val pkgNode = resolve.nodes.first { it.id == id }
        val nodeDeps = mutableListOf<CargoMetadata.Dep>()
        val nodeDependencies = mutableListOf<PackageId>()

        for (dep in pkgNode.deps.orEmpty()) {
            val depKinds = dep.dep_kinds?.filter { it.kind == null }.orEmpty()
            if (depKinds.isNotEmpty()) {
                nodeDependencies += dep.pkg.toStdlibId()
                nodeDeps += dep.copy(pkg = dep.pkg.toStdlibId(), dep_kinds = depKinds)
                walk(dep.pkg, false)
            }
        }

        allNodes += pkgNode.copy(id = stdlibId, dependencies = nodeDependencies, deps = nodeDeps)

        val (newManifestPath, newTargets) = if (pkg.source != null) {
            val newTargets = pkg.targets.map { it.copy(src_path = it.src_path.remapPath(pkg.name, pkg.version)) }
            pkg.manifest_path.remapPath(pkg.name, pkg.version) to newTargets
        } else {
            pkg.manifest_path to pkg.targets
        }

        val newPkg = pkg.copy(
            id = stdlibId,
            manifest_path = newManifestPath,
            targets = newTargets,
            dependencies = pkg.dependencies.filter { it.kind == null }
        )

        allPackages += newPkg
    }

    private fun VirtualFile.collectPackageMetadata() {
        val manifest = findChild(CargoConstants.MANIFEST_FILE)
        // Don't try to get metadata without Cargo.toml, it will fail anyway
        if (manifest == null) {
            LOG.warn("There isn't `${CargoConstants.MANIFEST_FILE}` in `$path` directory")
            return
        }

        val metadataProject = cargo.fetchMetadata(
            project,
            pathAsPath,
            buildTargets = buildTargets,
            toolchainOverride = activeToolchain,
            environmentVariables = additionalEnvVariables(version),
            listener = listener
        ).unwrapOrElse {
            listener?.error(RsBundle.message("build.event.title.failed.to.fetch.stdlib.package.info"), it.message.orEmpty())
            throw it
        }

        val rootPackageId = metadataProject.workspace_members.first()
        metadataProject.walk(rootPackageId, true)
    }

    companion object {
        private val LOG: Logger = logger<StdlibDataFetcher>()
        private val RUSTC_1_72_BETA = "1.72.0-beta".parseSemVer()

        fun create(
            project: Project,
            srcDir: VirtualFile,
            version: RustcVersion,
            buildTargets: List<String>,
            activeToolchain: String?,
            listener: ProcessProgressListener?,
            cleanVendorDir: Boolean
        ): StdlibDataFetcher? {
            val cargo = project.toolchain?.cargo() ?: return null

            val testPackageSrcPaths = listOf(AutoInjectedCrates.TEST, "lib${AutoInjectedCrates.TEST}")
            val testPackageSrcDir = srcDir.findFirstFileByRelativePaths(testPackageSrcPaths)?.canonicalFile
                ?: return null
            val stdlibDependenciesDir = findStdlibDependencyDirectory(
                project,
                cargo,
                srcDir,
                testPackageSrcDir,
                version,
                activeToolchain,
                listener,
                cleanVendorDir
            ) ?: return null
            return StdlibDataFetcher(
                project,
                cargo,
                srcDir,
                version,
                testPackageSrcDir,
                stdlibDependenciesDir,
                buildTargets,
                activeToolchain,
                listener
            )
        }

        @VisibleForTesting
        fun stdlibVendorDir(srcDir: VirtualFile, version: RustcVersion): Path {
            val stdlibHash = stdlibHash(srcDir, version)
            return RsPathManager.stdlibDependenciesDir().resolve("${version.semver.parsedVersion}-$stdlibHash/vendor")
        }

        private fun findStdlibDependencyDirectory(
            project: Project,
            cargo: Cargo,
            srcDir: VirtualFile,
            testPackageSrcDir: VirtualFile,
            version: RustcVersion,
            activeToolchain: String?,
            listener: ProcessProgressListener?,
            cleanVendorDir: Boolean
        ): Path? {
            val stdlibVendor = stdlibVendorDir(srcDir, version)
            var stdlibVendorExists = stdlibVendor.exists()
            if (stdlibVendorExists && cleanVendorDir) {
                try {
                    stdlibVendor.delete(recursively = true)
                } catch (e: IOException) {
                    LOG.error(e)
                    return null
                }
                stdlibVendorExists = false
            }

            if (!stdlibVendorExists) {
                // `test` package depends on all other stdlib packages,
                // so it's enough to vendor only its dependencies
                cargo.vendorDependencies(
                    project,
                    testPackageSrcDir.pathAsPath,
                    stdlibVendor,
                    activeToolchain,
                    environmentVariables = additionalEnvVariables(version),
                    listener
                ).unwrapOrElse {
                    listener?.error(RsBundle.message("build.event.title.failed.to.load.stdlib.dependencies"), it.message.orEmpty())
                    LOG.error(it)
                    return null
                }
            }
            return stdlibVendor
        }

        private fun stdlibHash(srcDir: VirtualFile, version: RustcVersion): String {
            val pathHash = HashCode.compute(srcDir.path)
            val versionHash = HashCode.compute(version.commitHash ?: version.semver.parsedVersion)
            return HashCode.mix(pathHash, versionHash).toString()
        }

        private fun additionalEnvVariables(version: RustcVersion): EnvironmentVariablesData {
            // Starting from `1.72.0-beta.1` there is a nightly Cargo feature usage in the stdlib:
            // `cargo-features = ["public-dependency"]`
            // (see the tracking issue for the feature: https://github.com/rust-lang/rust/issues/44663)
            val addRustcBootstrap = version.semver >= RUSTC_1_72_BETA
            return if (addRustcBootstrap) {
                EnvironmentVariablesData.create(mapOf(RUSTC_BOOTSTRAP to "1"), /*passParentEnvs=*/ true)
            } else {
                EnvironmentVariablesData.DEFAULT
            }
        }
    }
}

fun StandardLibrary.asPackageData(rustcInfo: RustcInfo?): List<CargoWorkspaceData.Package> {
    if (!isHardcoded) return crates
    return crates.map { it.withProperEdition(rustcInfo) }
}

private fun PackageId.toStdlibId(): String = "(stdlib) $this"

private fun VirtualFile.findFirstFileByRelativePaths(paths: List<String>): VirtualFile? {
    for (path in paths) {
        val file = findFileByRelativePath(path)
        if (file != null) return file
    }
    return null
}

private fun CargoWorkspaceData.Package.withProperEdition(rustcInfo: RustcInfo?): CargoWorkspaceData.Package {
    val currentRustcVersion = rustcInfo?.version?.semver
    val edition = if (currentRustcVersion == null) {
        CargoWorkspace.Edition.EDITION_2015
    } else {
        CargoWorkspace.Edition.EDITION_2018
    }

    val newTargets = targets.map { it.copy(edition = edition) }
    return copy(targets = newTargets, edition = edition)
}
