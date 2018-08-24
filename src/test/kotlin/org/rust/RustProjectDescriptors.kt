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
import org.rust.cargo.project.workspace.CargoWorkspace.CrateType
import org.rust.cargo.project.workspace.CargoWorkspace.TargetKind
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.CargoWorkspaceData.Package
import org.rust.cargo.project.workspace.CargoWorkspaceData.Target
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.project.workspace.StandardLibrary
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import java.nio.file.Paths
import java.util.*

object DefaultDescriptor : RustProjectDescriptorBase()

object WithStdlibRustProjectDescriptor : WithRustup(DefaultDescriptor)

object WithStdlibAndDependencyRustProjectDescriptor : WithRustup(WithDependencyRustProjectDescriptor)

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

    protected fun testCargoPackage(contentRoot: String, name: String = "test-package") = Package(
        id = "$name 0.0.1",
        contentRootUrl = contentRoot,
        name = name,
        version = "0.0.1",
        targets = listOf(
            Target("$contentRoot/main.rs", name, TargetKind.BIN, listOf(CrateType.BIN)),
            Target("$contentRoot/lib.rs", name, TargetKind.LIB, listOf(CrateType.LIB))
        ),
        source = null,
        origin = PackageOrigin.WORKSPACE
    )
}

open class WithRustup(private val delegate: RustProjectDescriptorBase) : RustProjectDescriptorBase() {
    private val toolchain: RustToolchain? by lazy { RustToolchain.suggest() }

    private val rustup by lazy { toolchain?.rustup(Paths.get(".")) }
    val stdlib by lazy { (rustup?.downloadStdlib() as? Rustup.DownloadResult.Ok)?.value }

    override val skipTestReason: String?
        get() {
            if (rustup == null) return "No rustup"
            if (stdlib == null) return "No stdib"
            return null
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

object WithDependencyRustProjectDescriptor : RustProjectDescriptorBase() {
    private fun externalPackage(contentRoot: String, source: String?, name: String, targetName: String = name): Package {
        return Package(
            id = "$name 0.0.1",
            contentRootUrl = "",
            name = name,
            version = "0.0.1",
            targets = listOf(
                // don't use `FileUtil.join` here because it uses `File.separator`
                // which is system dependent although all other code uses `/` as separator
                Target(source?.let { "$contentRoot/$it" } ?: "", targetName, TargetKind.LIB, listOf(CrateType.BIN))
            ),
            source = source,
            origin = PackageOrigin.DEPENDENCY
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
            externalPackage(contentRoot, "dep-lib/lib.rs", "dep-lib", "dep-lib-target"),
            externalPackage(contentRoot, null, "nosrc-lib", "nosrc-lib-target"),
            externalPackage(contentRoot, "trans-lib/lib.rs", "trans-lib"))

        val depNodes = ArrayList<CargoWorkspaceData.DependencyNode>()
        depNodes.add(CargoWorkspaceData.DependencyNode(0, listOf(1, 2)))   // Our package depends on dep_lib and dep_nosrc_lib

        return CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(packages, mapOf(
            packages[0].id to setOf(packages[1].id, packages[2].id)
        )))
    }
}
