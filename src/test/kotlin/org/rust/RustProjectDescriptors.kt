/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.rust.cargo.project.model.RustcInfo
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.*
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.CargoWorkspaceData.Package
import org.rust.cargo.project.workspace.CargoWorkspaceData.Target
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import java.nio.file.Paths

object DefaultDescriptor : RustProjectDescriptorBase()

object WithStdlibRustProjectDescriptor : WithRustup(DefaultDescriptor)

object WithStdlibAndDependencyRustProjectDescriptor : WithRustup(WithDependencyRustProjectDescriptor)

object WithStdlibWithSymlinkRustProjectDescriptor : WithCustomStdlibRustProjectDescriptor(DefaultDescriptor, {
    System.getenv("RUST_SRC_WITH_SYMLINK")
})

open class RustProjectDescriptorBase : LightProjectDescriptor() {

    open val skipTestReason: String? = null

    open val rustcInfo: RustcInfo? = null

    final override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
        super.configureModule(module, model, contentEntry)
        if (skipTestReason != null) return

        val projectDir = contentEntry.file!!
        val ws = testCargoProject(module, projectDir.url)
        module.project.cargoProjects.createTestProject(projectDir, ws, rustcInfo)
    }

    open fun setUp(fixture: CodeInsightTestFixture) {}

    open fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace {
        val packages = listOf(testCargoPackage(contentRoot))
        return CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(packages, emptyMap()))
    }

    protected fun testCargoPackage(contentRoot: String, name: String = "test-package") = CargoWorkspaceData.Package(
        id = "$name 0.0.1",
        contentRootUrl = contentRoot,
        name = name,
        version = "0.0.1",
        targets = listOf(
            Target("$contentRoot/main.rs", name, TargetKind.BIN, listOf(CrateType.BIN), edition = Edition.EDITION_2015),
            Target("$contentRoot/lib.rs", name, TargetKind.LIB, listOf(CrateType.LIB), edition = Edition.EDITION_2015)
        ),
        source = null,
        origin = PackageOrigin.WORKSPACE,
        edition = Edition.EDITION_2015
    )
}

open class WithRustup(private val delegate: RustProjectDescriptorBase) : RustProjectDescriptorBase() {
    private val toolchain: RustToolchain? by lazy { RustToolchain.suggest() }

    private val rustup by lazy { toolchain?.rustup(Paths.get(".")) }
    val stdlib by lazy { (rustup?.downloadStdlib() as? Rustup.DownloadResult.Ok)?.value }

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
        return delegate.testCargoProject(module, contentRoot).withStdlib(stdlib)
    }

    override fun setUp(fixture: CodeInsightTestFixture) {
        delegate.setUp(fixture)
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

    override val skipTestReason: String? get() {
        if (stdlib == null) return "No stdlib"
        return delegate.skipTestReason
    }

    override fun testCargoProject(module: Module, contentRoot: String): CargoWorkspace =
        delegate.testCargoProject(module, contentRoot).withStdlib(stdlib!!)

    override fun setUp(fixture: CodeInsightTestFixture) {
        delegate.setUp(fixture)
    }
}

object WithDependencyRustProjectDescriptor : RustProjectDescriptorBase() {
    private fun externalPackage(
        contentRoot: String,
        source: String?,
        name: String,
        targetName: String = name,
        version: String = "0.0.1",
        origin: PackageOrigin = PackageOrigin.DEPENDENCY
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
                    TargetKind.LIB, listOf(CrateType.BIN), Edition.EDITION_2015)
            ),
            source = source,
            origin = origin,
            edition = Edition.EDITION_2015
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
            externalPackage("$contentRoot/trans-lib", "lib.rs", "trans-lib",
                origin = PackageOrigin.TRANSITIVE_DEPENDENCY),
            externalPackage("$contentRoot/dep-lib-new", "lib.rs", "dep-lib", "dep-lib-target",
                version = "0.0.2", origin = PackageOrigin.TRANSITIVE_DEPENDENCY)
        )

        return CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(packages, mapOf(
            // Our package depends on dep_lib 0.0.1 and nosrc_lib
            packages[0].id to setOf(packages[1].id, packages[2].id),
            // dep_lib 0.0.1 depends on dep_lib 0.0.2
            packages[1].id to setOf(packages[4].id)
        )))
    }
}
