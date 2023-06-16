/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.Urls
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.impl.DEFAULT_EDITION_FOR_TESTS
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.*
import org.rust.cargo.project.workspace.CargoWorkspace.*
import org.rust.cargo.project.workspace.CargoWorkspaceData.Dependency
import org.rust.cargo.project.workspace.CargoWorkspaceData.Package
import org.rust.cargo.project.workspace.CargoWorkspaceData.Target
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.cargo.toolchain.tools.cargo
import org.rust.cargo.toolchain.tools.rustc
import org.rust.cargo.toolchain.tools.rustup
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.cargo.util.DownloadResult
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.RsPathManager
import org.rust.stdext.HashCode
import org.rust.stdext.unwrapOrThrow
import org.rustPerformanceTests.fullyRefreshDirectoryInUnitTests
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object DefaultDescriptor : RustProjectDescriptorBase()

object WithStdlibRustProjectDescriptor : WithRustup(DefaultDescriptor)

object WithActualStdlibRustProjectDescriptor : WithRustup(DefaultDescriptor, fetchActualStdlibMetadata = true)

object WithStdlibAndDependencyRustProjectDescriptor : WithRustup(WithDependencyRustProjectDescriptor)

object WithStdlibWithSymlinkRustProjectDescriptor : WithCustomStdlibRustProjectDescriptor(DefaultDescriptor, {
    val path = System.getenv("RUST_SRC_WITH_SYMLINK")
    // FIXME: find out why it doesn't work on CI on Windows
    if (System.getenv("CI") != null && !SystemInfo.isWindows) {
        if (path == null) error("`RUST_SRC_WITH_SYMLINK` environment variable is not set")
        if (!File(path).exists()) error("`$path` doesn't exist")
    }
    path
})

/**
 * Constructs a project with dependency `testData/test-proc-macros`
 */
object WithProcMacroRustProjectDescriptor : WithProcMacros(DefaultDescriptor)
object WithProcMacroAndDependencyRustProjectDescriptor : WithProcMacros(WithDependencyRustProjectDescriptor)
object WithProcMacroAndStdlibRustProjectDescriptor : WithProcMacros(WithStdlibRustProjectDescriptor)

open class RustProjectDescriptorBase {

    open val skipTestReason: String? = null
    open val rustcInfo: RustcInfo? = null
    open val macroExpansionCachingKey: String? = null

    open fun setUp(fixture: CodeInsightTestFixture) {}

    open fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace? {
        val packages = listOf(testCargoPackage(contentRoot))
        return CargoWorkspace.deserialize(
            Paths.get("${Urls.newFromIdea(contentRoot).path}/workspace/Cargo.toml"),
            CargoWorkspaceData(packages, emptyMap(), emptyMap(), contentRoot),
        )
    }

    protected open fun externalPackage(
        contentRoot: String,
        source: String?,
        name: String,
        targetName: String = name,
        version: String = "0.0.1",
        origin: PackageOrigin = PackageOrigin.DEPENDENCY,
        libKind: LibKind = LibKind.LIB,
        procMacroArtifact: CargoWorkspaceData.ProcMacroArtifact? = null,
    ): Package {
        return Package(
            id = "$name $version",
            contentRootUrl = contentRoot,
            name = name,
            version = version,
            targets = listOf(
                // don't use `FileUtil.join` here because it uses `File.separator`
                // which is system dependent although all other code uses `/` as separator
                Target(source?.let { "$contentRoot/$it" } ?: "",
                    targetName,
                    TargetKind.Lib(libKind),
                    DEFAULT_EDITION_FOR_TESTS,
                    doctest = true,
                    requiredFeatures = emptyList()
                )
            ),
            source = source,
            origin = origin,
            edition = DEFAULT_EDITION_FOR_TESTS,
            features = emptyMap(),
            enabledFeatures = emptySet(),
            cfgOptions = CfgOptions.EMPTY,
            env = emptyMap(),
            outDirUrl = null,
            procMacroArtifact = procMacroArtifact
        )
    }

    protected open fun testCargoPackage(contentRoot: String, name: String = "test-package") = Package(
        id = "$name 0.0.1",
        contentRootUrl = contentRoot,
        name = name,
        version = "0.0.1",
        targets = listOf(
            testTarget("$contentRoot/main.rs", name, TargetKind.Bin),
            testTarget("$contentRoot/lib.rs", name, TargetKind.Lib(LibKind.LIB)),
            testTarget("$contentRoot/bin/a.rs", name, TargetKind.Bin),
            testTarget("$contentRoot/bin/a/main.rs", name, TargetKind.Bin),
            testTarget("$contentRoot/tests/a.rs", name, TargetKind.Test),
            testTarget("$contentRoot/tests/b.rs", name, TargetKind.Test),
            testTarget("$contentRoot/tests/a/main.rs", name, TargetKind.Test),
            testTarget("$contentRoot/bench/a.rs", name, TargetKind.Bench),
            testTarget("$contentRoot/bench/a/main.rs", name, TargetKind.Bench),
            testTarget("$contentRoot/example/a.rs", name, TargetKind.ExampleBin),
            testTarget("$contentRoot/example/a/main.rs", name, TargetKind.ExampleBin),
            testTarget("$contentRoot/example-lib/a.rs", name, TargetKind.ExampleLib(EnumSet.of(LibKind.LIB))),
            testTarget("$contentRoot/build.rs", "build_script_build", TargetKind.CustomBuild, doctest = false),
        ),
        source = null,
        origin = PackageOrigin.WORKSPACE,
        edition = DEFAULT_EDITION_FOR_TESTS,
        features = emptyMap(),
        enabledFeatures = emptySet(),
        cfgOptions = CfgOptions.EMPTY,
        env = emptyMap(),
        outDirUrl = null
    )

    protected fun testTarget(crateRootUrl: String, name: String, kind: TargetKind, doctest: Boolean = true): Target =
        Target(crateRootUrl, name, kind, DEFAULT_EDITION_FOR_TESTS, doctest, requiredFeatures = emptyList())
}

object EmptyDescriptor : RustProjectDescriptorBase() {
    override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace? {
        return null
    }
}

open class WithRustup(
    private val delegate: RustProjectDescriptorBase,
    private val fetchActualStdlibMetadata: Boolean = false
) : RustProjectDescriptorBase() {
    private val toolchain: RsToolchainBase? by lazy { RsToolchainBase.suggest() }

    private val rustup: Rustup? by lazy { toolchain?.rustup(Paths.get(".")) }
    val stdlib: VirtualFile? by lazy { (rustup?.downloadStdlib() as? DownloadResult.Ok)?.value }

    private val cfgOptions: CfgOptions? by lazy {
        toolchain?.rustc()?.getCfgOptions(null)
    }

    override val skipTestReason: String?
        get() {
            if (rustup == null) return "No rustup"
            if (stdlib == null) return "No stdlib"
            return delegate.skipTestReason
        }

    override val rustcInfo: RustcInfo? by lazy {
        toolchain?.getRustcInfo()
    }

    override val macroExpansionCachingKey: String?
        get() = if (fetchActualStdlibMetadata) "actual_std" else "std"

    private var hardcodedStdlibIsInitialized: Boolean = false
    private var hardcodedStdlib: StandardLibrary? = null

    private fun getOrFetchHardcodedStdlib(project: Project): StandardLibrary? = if (hardcodedStdlibIsInitialized) {
        hardcodedStdlib
    } else {
        hardcodedStdlibIsInitialized = true
        val disposable = Disposer.newDisposable("testCargoProject")
        try {
            VfsRootAccess.allowRootAccess(disposable, stdlib!!.path)
            hardcodedStdlib = StandardLibrary.fromFile(project, stdlib!!, rustcInfo)
            hardcodedStdlib
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private var actualStdlibIsInitialized: Boolean = false
    private var actualStdlib: StandardLibrary? = null

    private fun getOrFetchActualStdlib(project: Project): StandardLibrary? = if (actualStdlibIsInitialized) {
        actualStdlib
    } else {
        actualStdlibIsInitialized = true
        val disposable = Disposer.newDisposable("testCargoProject")
        try {
            project.rustSettings.modifyTemporary(disposable) {
                it.toolchain = toolchain
            }
            // RsExperiments.FETCH_ACTUAL_STDLIB_METADATA significantly slows down tests
            setExperimentalFeatureEnabled(RsExperiments.FETCH_ACTUAL_STDLIB_METADATA, true, disposable)

            actualStdlib = StandardLibrary.fromFile(project, stdlib!!, rustcInfo)
            actualStdlib
        } finally {
            Disposer.dispose(disposable)
        }
    }

    override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace {
        val stdlib = if (fetchActualStdlibMetadata) {
            getOrFetchActualStdlib(project)
        } else {
            getOrFetchHardcodedStdlib(project)
        }!!
        val cfgOptions = cfgOptions!!
        return delegate.createTestCargoWorkspace(project, contentRoot)!!.withStdlib(stdlib, cfgOptions, rustcInfo)
    }

    override fun setUp(fixture: CodeInsightTestFixture) {
        delegate.setUp(fixture)
        stdlib?.let { VfsRootAccess.allowRootAccess(fixture.testRootDisposable, it.path) }
        // TODO: use RustupTestFixture somehow
        val rustSettings = fixture.project.rustSettings
        rustSettings.modifyTemporary(fixture.testRootDisposable) {
            it.toolchain = toolchain
        }
    }
}

/** Adds `testData/test-proc-macros` package to dependencies of each package of the project */
open class WithProcMacros(
    private val delegate: RustProjectDescriptorBase
) : RustProjectDescriptorBase() {
    override val skipTestReason: String?
        get() = ProcMacroPackageHolder.skipTestReason ?: delegate.skipTestReason

    override val rustcInfo: RustcInfo?
        get() = delegate.rustcInfo ?: ProcMacroPackageHolder.rustcInfo

    override val macroExpansionCachingKey: String?
        get() = delegate.macroExpansionCachingKey

    override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace {
        val procMacroPackage1 = ProcMacroPackageHolder.getOrFetchMacroPackage(project)
        check(procMacroPackage1 != null) { "Proc macro crate is not compiled successfully" }
        return delegate.createTestCargoWorkspace(project, contentRoot)!!.withImplicitDependency(procMacroPackage1)
    }

    override fun setUp(fixture: CodeInsightTestFixture) {
        delegate.setUp(fixture)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as WithProcMacros

        if (delegate != other.delegate) return false

        return true
    }

    override fun hashCode(): Int {
        return delegate.hashCode()
    }

    companion object {
        const val TEST_PROC_MACROS: String = "test-proc-macros"
    }
}

private object ProcMacroPackageHolder {
    private val toolchain: RsToolchainBase? by lazy { RsToolchainBase.suggest() }

    private var procMacroPackageIsInitialized: Boolean = false
    private var procMacroPackage: Package? = null

    val skipTestReason: String?
        get() {
            if (toolchain == null) {
                return "No toolchain"
            }
            if (RsPathManager.nativeHelper(toolchain is RsWslToolchain) == null &&
                System.getenv("CI") == null) {
                return "no native-helper executable"
            }
            return null
        }

    val rustcInfo: RustcInfo? by lazy {
        toolchain?.getRustcInfo()
    }

    fun getOrFetchMacroPackage(project: Project): Package? = if (procMacroPackageIsInitialized) {
        procMacroPackage
    } else {
        procMacroPackageIsInitialized = true
        val disposable = Disposer.newDisposable("testCargoProject")
        try {
            setExperimentalFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS, true, disposable)
            val testProcMacroProjectPath = Path.of("testData/${WithProcMacros.TEST_PROC_MACROS}")
            val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(testProcMacroProjectPath)
                ?: error("test-proc-macros project is not found in the VFS! " +
                    "Absolute path: ${testProcMacroProjectPath.toAbsolutePath()}")
            fullyRefreshDirectoryInUnitTests(virtualFile)
            val (testProcMacroProject, _) = toolchain!!.cargo().fullProjectDescription(
                project,
                testProcMacroProjectPath,
                rustcVersion = rustcInfo?.version,
            ).unwrapOrThrow()
            procMacroPackage = testProcMacroProject.packages.find { it.name == WithProcMacros.TEST_PROC_MACROS }!!
                .copy(origin = PackageOrigin.DEPENDENCY)
            procMacroPackage
        } finally {
            Disposer.dispose(disposable)
        }
    }
}

open class WithCustomStdlibRustProjectDescriptor(
    private val delegate: RustProjectDescriptorBase,
    private val explicitStdlibPath: () -> String?
) : RustProjectDescriptorBase() {

    override val skipTestReason: String?
        get() {
            if (explicitStdlibPath() == null) return "No stdlib"
            return delegate.skipTestReason
        }

    override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace? {
        val path = explicitStdlibPath() ?: return null
        val stdlib = StandardLibrary.fromPath(project, path, rustcInfo)!!
        return delegate.createTestCargoWorkspace(project, contentRoot)!!.withStdlib(stdlib, CfgOptions.DEFAULT)
    }

    override fun setUp(fixture: CodeInsightTestFixture) {
        delegate.setUp(fixture)
        val stdlibPath = explicitStdlibPath() ?: return
        val file = LocalFileSystem.getInstance().findFileByPath(stdlibPath)
        VfsRootAccess.allowRootAccess(fixture.testRootDisposable, *listOfNotNull(file?.path, file?.canonicalPath).toTypedArray())
    }
}

object WithDependencyRustProjectDescriptor : RustProjectDescriptorBase() {

    override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace {
        val testProcMacroArtifact1 = CargoWorkspaceData.ProcMacroArtifact(
            Path.of("/test/proc_macro_artifact"), // The file does not exists
            HashCode.compute("test")
        )
        val testProcMacroArtifact2 = CargoWorkspaceData.ProcMacroArtifact(
            Path.of("/test/proc_macro_artifact2"), // The file does not exists
            HashCode.compute("test2")
        )

        val testPackage = testCargoPackage(contentRoot)
        val depLib = externalPackage("$contentRoot/dep-lib", "lib.rs", "dep-lib", "dep-lib-target")
        val depLibNew = externalPackage(
            "$contentRoot/dep-lib-new", "lib.rs", "dep-lib", "dep-lib-target",
            version = "0.0.2"
        )
        val depLib2 = externalPackage("$contentRoot/dep-lib-2", "lib.rs", "dep-lib-2", "dep-lib-target-2")
        val depLibWithCyclicDep = externalPackage("$contentRoot/dep-lib-with-cyclic-dep", "lib.rs", "dep-lib-with-cyclic-dep")
        val depLibToBeRenamed = externalPackage(
            "$contentRoot/dep-lib-to-be-renamed", "lib.rs", "dep-lib-to-be-renamed",
            "dep-lib-to-be-renamed-target"
        )
        val noSrcLib = externalPackage("", null, "nosrc-lib", "nosrc-lib-target")
        val noSourceLib = externalPackage("$contentRoot/no-source-lib", "lib.rs", "no-source-lib").copy(source = null)
        val transLib = externalPackage("$contentRoot/trans-lib", "lib.rs", "trans-lib")
        val transLib2 = externalPackage("$contentRoot/trans-lib-2", "lib.rs", "trans-lib-2")
        val transCommonLib = externalPackage("$contentRoot/trans-common-lib", "lib.rs", "trans-common-lib")
        val rawIdentifierLib = externalPackage("$contentRoot/loop", "lib.rs", "loop")
        val depProcMacro = externalPackage(
            "$contentRoot/dep-proc-macro", "lib.rs", "dep-proc-macro", libKind = LibKind.PROC_MACRO,
            procMacroArtifact = testProcMacroArtifact1
        )
        val depProcMacro2 = externalPackage("$contentRoot/dep-proc-macro-2", "lib.rs", "dep-proc-macro-2", libKind = LibKind.PROC_MACRO,
            procMacroArtifact = testProcMacroArtifact2)
        val depProcMacro3 = externalPackage("$contentRoot/dep-proc-macro-unsuccessfully-compiled", "lib.rs", "dep-proc-unsuccessfully-compiled", libKind = LibKind.PROC_MACRO,
            procMacroArtifact = null)
        val cyclicDepLibDevDep = externalPackage("$contentRoot/cyclic-dep-lib-dev-dep", "lib.rs", "cyclic-dep-lib-dev-dep")

        val packages = listOf(
            testPackage, depLib, depLibNew, depLib2, depLibWithCyclicDep, depLibToBeRenamed,
            noSrcLib, noSourceLib, transLib, transLib2, transCommonLib, rawIdentifierLib, depProcMacro, depProcMacro2,
            depProcMacro3, cyclicDepLibDevDep
        )

        return CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(packages, mapOf(
            testPackage.id to setOf(
                dep(depLib.id),
                dep(depLib2.id),
                dep(depLibToBeRenamed.id, "dep_lib_renamed"),
                dep(noSrcLib.id),
                dep(noSourceLib.id),
                dep(depProcMacro.id),
                dep(depProcMacro2.id),
                dep(depProcMacro3.id),
                dep(rawIdentifierLib.id),
            ),
            depLib.id to setOf(
                dep(transLib.id),
                dep(depLibNew.id),
                dep(transCommonLib.id),
            ),
            depLib2.id to setOf(
                dep(transCommonLib.id),
            ),
            depLibWithCyclicDep.id to setOf(
                dep(cyclicDepLibDevDep.id, depKind = DepKind.Development),
            ),
            transLib.id to setOf(
                dep(transLib2.id),
            ),
            cyclicDepLibDevDep.id to setOf(
                dep(depLibWithCyclicDep.id),
            )
        ), emptyMap(), contentRoot))
    }

    private fun dep(id: PackageId, name: String? = null, depKind: DepKind = DepKind.Normal): Dependency =
        Dependency(id, name, listOf(DepKindInfo(depKind)))
}

private class WithStdlibLikeDependencyRustProjectDescriptor : RustProjectDescriptorBase() {
    override fun createTestCargoWorkspace(project: Project, contentRoot: String): CargoWorkspace {
        val packages = listOf(
            testCargoPackage(contentRoot),
            externalPackage("$contentRoot/core", "lib.rs", "core"),
            externalPackage("$contentRoot/alloc", "lib.rs", "alloc"),
            externalPackage("$contentRoot/std", "lib.rs", "std")
        )
        return CargoWorkspace.deserialize(
            Paths.get("${Urls.newFromIdea(contentRoot).path}/workspace/Cargo.toml"),
            CargoWorkspaceData(packages, emptyMap(), emptyMap(), contentRoot),
        )
    }
}

/**
 * Provides `core`, `alloc` and `std` workspace dependencies.
 * It's supposed to be used to check how the plugin works with dependencies that have the same name as stdlib packages
 */
object WithStdlibAndStdlibLikeDependencyRustProjectDescriptor : WithRustup(WithStdlibLikeDependencyRustProjectDescriptor())

/**
 * The same as [org.rust.DefaultDescriptor] but it provides test package with [PackageOrigin.STDLIB] origin instead of [PackageOrigin.WORKSPACE]
 */
object StdlibLikeProjectDescriptor : RustProjectDescriptorBase() {

    override fun testCargoPackage(contentRoot: String, name: String): Package {
        return super.testCargoPackage(contentRoot, name).copy(origin = PackageOrigin.STDLIB)
    }
}

private fun RsToolchainBase.getRustcInfo(): RustcInfo? {
    val rustc = rustc()
    val sysroot = rustc.getSysroot(Paths.get(".")) ?: return null
    val rustcVersion = rustc.queryVersion()
    return RustcInfo(sysroot, rustcVersion)
}
