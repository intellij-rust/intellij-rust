/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.Urls
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.*
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.CargoWorkspaceData.Dependency
import org.rust.cargo.project.workspace.CargoWorkspaceData.Package
import org.rust.cargo.project.workspace.CargoWorkspaceData.Target
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.toolchain.RsToolchain
import org.rust.cargo.toolchain.tools.cargo
import org.rust.cargo.toolchain.tools.rustc
import org.rust.cargo.toolchain.tools.rustup
import org.rust.cargo.util.DownloadResult
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.RsPathManager
import org.rustPerformanceTests.fullyRefreshDirectoryInUnitTests
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

object DefaultDescriptor : RustProjectDescriptorBase()

object EmptyDescriptor : LightProjectDescriptor()

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

open class RustProjectDescriptorBase : LightProjectDescriptor() {

    open val skipTestReason: String? = null

    open val rustcInfo: RustcInfo? = null

    final override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        if (skipTestReason != null) return

        val projectDir = contentEntry.file!!
        val ws = testCargoProject(module, projectDir.url)
        module.project.testCargoProjects.createTestProject(projectDir, ws, rustcInfo)
    }

    open fun setUp(fixture: CodeInsightTestFixture) {}

    open fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
        val packages = listOf(testCargoPackage(contentRoot))
        return CargoWorkspace.deserialize(Paths.get("${Urls.newFromIdea(contentRoot).path}/workspace/Cargo.toml"),
            CargoWorkspaceData(packages, emptyMap(), emptyMap()), CfgOptions.DEFAULT)
    }

    protected fun testCargoPackage(contentRoot: String, name: String = "test-package") = Package(
        id = "$name 0.0.1",
        contentRootUrl = contentRoot,
        name = name,
        version = "0.0.1",
        targets = listOf(
            Target("$contentRoot/main.rs", name, TargetKind.Bin, edition = Edition.EDITION_2015, doctest = true, requiredFeatures = emptyList()),
            Target("$contentRoot/lib.rs", name, TargetKind.Lib(LibKind.LIB), edition = Edition.EDITION_2015, doctest = true, requiredFeatures = emptyList()),
            Target("$contentRoot/bin/a.rs", name, TargetKind.Bin, edition = Edition.EDITION_2015, doctest = true, requiredFeatures = emptyList()),
            Target("$contentRoot/bench/a.rs", name, TargetKind.Bench, edition = Edition.EDITION_2015, doctest = true, requiredFeatures = emptyList()),
            Target("$contentRoot/example/a.rs", name, TargetKind.ExampleBin, edition = Edition.EDITION_2015, doctest = true, requiredFeatures = emptyList()),
            Target("$contentRoot/example-lib/a.rs", name, TargetKind.ExampleLib(EnumSet.of(LibKind.LIB)), edition = Edition.EDITION_2015, doctest = true, requiredFeatures = emptyList()),
            Target("$contentRoot/build.rs", "build_script_build", TargetKind.CustomBuild, edition = Edition.EDITION_2015, doctest = false, requiredFeatures = emptyList())
        ),
        source = null,
        origin = PackageOrigin.WORKSPACE,
        edition = Edition.EDITION_2015,
        features = emptyMap(),
        enabledFeatures = emptySet(),
        cfgOptions = CfgOptions.EMPTY,
        env = emptyMap(),
        outDirUrl = null
    )
}

open class WithRustup(
    private val delegate: RustProjectDescriptorBase,
    private val fetchActualStdlibMetadata: Boolean = false
) : RustProjectDescriptorBase() {
    private val toolchain: RsToolchain? by lazy { RsToolchain.suggest() }

    private val rustup by lazy { toolchain?.rustup(Paths.get(".")) }
    val stdlib by lazy { (rustup?.downloadStdlib() as? DownloadResult.Ok)?.value }

    private val cfgOptions by lazy {
        toolchain?.rustc()?.getCfgOptions(null)
    }

    override val skipTestReason: String?
        get() {
            if (rustup == null) return "No rustup"
            if (stdlib == null) return "No stdlib"
            return delegate.skipTestReason
        }

    override val rustcInfo: RustcInfo?
        get() = toolchain?.getRustcInfo()

    override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
        val disposable = Disposer.newDisposable("testCargoProject")
        // TODO: use RustupTestFixture somehow
        module.project.rustSettings.modifyTemporary(disposable) {
            it.toolchain = toolchain
        }
        try {
            // RsExperiments.FETCH_ACTUAL_STDLIB_METADATA significantly slows down tests
            setExperimentalFeatureEnabled(RsExperiments.FETCH_ACTUAL_STDLIB_METADATA, fetchActualStdlibMetadata, disposable)

            val rustcInfo = rustcInfo
            val stdlib = StandardLibrary.fromFile(module.project, stdlib!!, rustcInfo)!!
            val cfgOptions = cfgOptions!!
            return delegate.testCargoProject(module, contentRoot).withStdlib(stdlib, cfgOptions, rustcInfo)
        } finally {
            Disposer.dispose(disposable)
        }
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
    private val toolchain: RsToolchain? by lazy { RsToolchain.suggest() }

    private var procMacroPackageIsInitialized: Boolean = false
    private var procMacroPackage: Package? = null

    override val skipTestReason: String?
        get() {
            if (toolchain == null) {
                return "No toolchain"
            }
            if (RsPathManager.nativeHelper() == null && System.getenv("CI") == null) {
                return "no native-helper executable"
            }
            return delegate.skipTestReason
        }

    override val rustcInfo: RustcInfo?
        get() = delegate.rustcInfo ?: toolchain?.getRustcInfo()

    private fun getOrFetchMacroPackage(project: Project): Package? = if (procMacroPackageIsInitialized) {
        procMacroPackage
    } else {
        procMacroPackageIsInitialized = true
        val disposable = Disposer.newDisposable("testCargoProject")
        try {
            setExperimentalFeatureEnabled(RsExperiments.EVALUATE_BUILD_SCRIPTS, true, disposable)
            val testProcMacroProjectPath = Path.of("testData/$TEST_PROC_MACROS")
            fullyRefreshDirectoryInUnitTests(LocalFileSystem.getInstance().findFileByNioFile(testProcMacroProjectPath)!!)
            val testProcMacroProject = toolchain!!.cargo().fullProjectDescription(project, testProcMacroProjectPath)
            procMacroPackage = testProcMacroProject.packages.find { it.name == TEST_PROC_MACROS }!!
                .copy(origin = PackageOrigin.DEPENDENCY)
            procMacroPackage
        } finally {
            Disposer.dispose(disposable)
        }
    }

    override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
        val procMacroPackage1 = getOrFetchMacroPackage(module.project)
        check(procMacroPackage1 != null) { "Proc macro crate is not compiled successfully" }
        return delegate.testCargoProject(module, contentRoot).withImplicitDependency(procMacroPackage1)
    }

    override fun setUp(fixture: CodeInsightTestFixture) {
        delegate.setUp(fixture)
    }

    companion object {
        const val TEST_PROC_MACROS: String = "test-proc-macros"
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

    override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
        val stdlib = StandardLibrary.fromPath(module.project, explicitStdlibPath()!!, rustcInfo)!!
        return delegate.testCargoProject(module, contentRoot).withStdlib(stdlib, CfgOptions.DEFAULT)
    }

    override fun setUp(fixture: CodeInsightTestFixture) {
        delegate.setUp(fixture)
        val stdlibPath = explicitStdlibPath() ?: return
        val file = LocalFileSystem.getInstance().findFileByPath(stdlibPath)
        VfsRootAccess.allowRootAccess(fixture.testRootDisposable, *listOfNotNull(file?.path, file?.canonicalPath).toTypedArray())
    }
}

object WithDependencyRustProjectDescriptor : RustProjectDescriptorBase() {
    private fun externalPackage(
        contentRoot: String,
        source: String?,
        name: String,
        targetName: String = name,
        version: String = "0.0.1",
        origin: PackageOrigin = PackageOrigin.DEPENDENCY,
        libKind: LibKind = LibKind.LIB
    ): Package {
        return Package(
            id = "$name $version",
            contentRootUrl = contentRoot,
            name = name,
            version = version,
            targets = listOf(
                // don't use `FileUtil.join` here because it uses `File.separator`
                // which is system dependent although all other code uses `/` as separator
                Target(source?.let { "$contentRoot/$it" } ?: "", targetName,
                    TargetKind.Lib(libKind), Edition.EDITION_2015, doctest = true, requiredFeatures = emptyList())
            ),
            source = source,
            origin = origin,
            edition = Edition.EDITION_2015,
            features = emptyMap(),
            enabledFeatures = emptySet(),
            cfgOptions = CfgOptions.EMPTY,
            env = emptyMap(),
            outDirUrl = null
        )
    }

    override fun setUp(fixture: CodeInsightTestFixture) {
        val root = fixture.findFileInTempDir(".")!!
        for (source in listOf("dep-lib/lib.rs", "trans-lib/lib.rs")) {
            VfsTestUtil.createFile(root, source)
        }
    }

    override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
        val packages = listOf(
            testCargoPackage(contentRoot),
            externalPackage("$contentRoot/dep-lib", "lib.rs", "dep-lib", "dep-lib-target"),
            externalPackage("", null, "nosrc-lib", "nosrc-lib-target"),
            externalPackage("$contentRoot/trans-lib", "lib.rs", "trans-lib"),
            externalPackage("$contentRoot/dep-lib-new", "lib.rs", "dep-lib", "dep-lib-target",
                version = "0.0.2"),
            externalPackage("$contentRoot/dep-proc-macro", "lib.rs", "dep-proc-macro", libKind = LibKind.PROC_MACRO),
            externalPackage("$contentRoot/dep-lib-2", "lib.rs", "dep-lib-2", "dep-lib-target-2"),
            externalPackage("$contentRoot/trans-lib-2", "lib.rs", "trans-lib-2"),
            externalPackage("$contentRoot/no-source-lib", "lib.rs", "no-source-lib").copy(source = null),
            externalPackage("$contentRoot/dep-lib-to-be-renamed", "lib.rs", "dep-lib-to-be-renamed-target"),
        )

        return CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(packages, mapOf(
            // Our package depends on dep_lib 0.0.1, nosrc_lib, dep-proc-macro, dep_lib-2 and no-source-lib
            packages[0].id to setOf(
                Dependency(packages[1].id),
                Dependency(packages[2].id),
                Dependency(packages[5].id),
                Dependency(packages[6].id),
                Dependency(packages[8].id),
                Dependency(packages[9].id, "dep_lib_renamed"),
            ),
            // dep_lib 0.0.1 depends on trans-lib and dep_lib 0.0.2
            packages[1].id to setOf(
                Dependency(packages[3].id),
                Dependency(packages[4].id)
            ),
            // trans-lib depends on trans-lib-2
            packages[3].id to setOf(
                Dependency(packages[7].id)
            )
        ), emptyMap()), CfgOptions.DEFAULT)
    }
}

private fun RsToolchain.getRustcInfo(): RustcInfo? {
    val rustc = rustc()
    val sysroot = rustc.getSysroot(Paths.get(".")) ?: return null
    val rustcVersion = rustc.queryVersion()
    return RustcInfo(sysroot, rustcVersion)
}
