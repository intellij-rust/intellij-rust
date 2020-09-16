/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
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
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.util.DownloadResult
import java.io.File
import java.nio.file.Paths
import java.util.*

object DefaultDescriptor : RustProjectDescriptorBase()

object EmptyDescriptor : LightProjectDescriptor()

object WithStdlibRustProjectDescriptor : WithRustup(DefaultDescriptor)

object WithStdlibAndDependencyRustProjectDescriptor : WithRustup(WithDependencyRustProjectDescriptor)

object WithStdlibWithSymlinkRustProjectDescriptor : WithCustomStdlibRustProjectDescriptor(DefaultDescriptor, {
    val path = System.getenv("RUST_SRC_WITH_SYMLINK")
    if (System.getenv("CI") != null) {
        if (path == null) error("`RUST_SRC_WITH_SYMLINK` environment variable is not set")
        if (!File(path).exists()) error("`$path` doesn't exist")
    }
    path
})

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
            CargoWorkspaceData(packages, emptyMap()), CfgOptions.DEFAULT)
    }

    protected fun testCargoPackage(contentRoot: String, name: String = "test-package") = Package(
        id = "$name 0.0.1",
        contentRootUrl = contentRoot,
        name = name,
        version = "0.0.1",
        targets = listOf(
            Target("$contentRoot/main.rs", name, TargetKind.Bin, edition = Edition.EDITION_2015, doctest = true),
            Target("$contentRoot/lib.rs", name, TargetKind.Lib(LibKind.LIB), edition = Edition.EDITION_2015, doctest = true),
            Target("$contentRoot/bin/a.rs", name, TargetKind.Bin, edition = Edition.EDITION_2015, doctest = true),
            Target("$contentRoot/bench/a.rs", name, TargetKind.Bench, edition = Edition.EDITION_2015, doctest = true),
            Target("$contentRoot/example/a.rs", name, TargetKind.ExampleBin, edition = Edition.EDITION_2015, doctest = true),
            Target("$contentRoot/example-lib/a.rs", name, TargetKind.ExampleLib(EnumSet.of(LibKind.LIB)), edition = Edition.EDITION_2015, doctest = true),
            Target("$contentRoot/build.rs", "build_script_build", TargetKind.CustomBuild, edition = Edition.EDITION_2015, doctest = false)
        ),
        source = null,
        origin = PackageOrigin.WORKSPACE,
        edition = Edition.EDITION_2015,
        features = emptyList(),
        cfgOptions = CfgOptions.EMPTY,
        env = emptyMap(),
        outDirUrl = null
    )
}

open class WithRustup(private val delegate: RustProjectDescriptorBase) : RustProjectDescriptorBase() {
    private val toolchain: RustToolchain? by lazy { RustToolchain.suggest() }

    private val rustup by lazy { toolchain?.rustup(Paths.get(".")) }
    val stdlib by lazy { (rustup?.downloadStdlib() as? DownloadResult.Ok)?.value }

    override val skipTestReason: String?
        get() {
            if (rustup == null) return "No rustup"
            if (stdlib == null) return "No stdlib"
            return delegate.skipTestReason
        }

    override val rustcInfo: RustcInfo?
        get() {
            val toolchain = toolchain ?: return null
            val sysroot = toolchain.getSysroot(Paths.get(".")) ?: return null
            val rustcVersion = toolchain.queryVersions().rustc
            return RustcInfo(sysroot, rustcVersion)
        }

    override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
        val stdlib = StandardLibrary.fromFile(stdlib!!)!!
        return delegate.testCargoProject(module, contentRoot).withStdlib(stdlib, CfgOptions.DEFAULT, rustcInfo)
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

open class WithCustomStdlibRustProjectDescriptor(
    private val delegate: RustProjectDescriptorBase,
    private val explicitStdlibPath: () -> String?
) : RustProjectDescriptorBase() {

    private val stdlib: StandardLibrary? by lazy {
        val path = explicitStdlibPath() ?: return@lazy null
        StandardLibrary.fromPath(path)
    }

    override val skipTestReason: String?
        get() {
            if (stdlib == null) return "No stdlib"
            return delegate.skipTestReason
        }

    override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace =
        delegate.testCargoProject(module, contentRoot).withStdlib(stdlib!!, CfgOptions.DEFAULT)

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
                    TargetKind.Lib(libKind), Edition.EDITION_2015, doctest = true)
            ),
            source = source,
            origin = origin,
            edition = Edition.EDITION_2015,
            features = emptyList(),
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
            externalPackage("$contentRoot/no-source-lib", "lib.rs", "no-source-lib").copy(source = null)
        )

        return CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(packages, mapOf(
            // Our package depends on dep_lib 0.0.1, nosrc_lib, dep-proc-macro, dep_lib-2 and no-source-lib
            packages[0].id to setOf(
                Dependency(packages[1].id),
                Dependency(packages[2].id),
                Dependency(packages[5].id),
                Dependency(packages[6].id),
                Dependency(packages[8].id)
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
        )), CfgOptions.DEFAULT)
    }
}
