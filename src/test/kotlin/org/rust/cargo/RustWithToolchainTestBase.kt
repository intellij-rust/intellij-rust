package org.rust.cargo

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.PlatformTestCase.copyDirContentsTo
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoProjectWorkspaceService
import org.rust.cargo.project.workspace.cargoWorkspace
import org.rust.cargo.toolchain.RustToolchain

// This class allows to execute real Cargo during the tests.
// Unlike `RustTestCaseBase` it does not use in-memory temporary VFS
// and instead copies real files.
abstract class RustWithToolchainTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {
    open val dataPath: String = ""

    private val toolchain = RustToolchain.suggest()

    protected val cargoProjectDirectory: VirtualFile get() = myFixture.findFileInTempDir(".")

    protected fun withProject(projectName: String, action: () -> Unit) {
        val projectDirectory = "$dataPath/$projectName"
        val data = LocalFileSystem.getInstance().findFileByPath(projectDirectory)
            ?: throw RuntimeException("No such directory: $projectDirectory")

        data.refresh(/* asynchronous = */ false, /* recursive = */ true)
        copyDirContentsTo(data, cargoProjectDirectory)

        action()
    }

    protected fun refreshWorkspace() {
        CargoProjectWorkspaceService.getInstance(myModule).syncUpdate(myModule.project.toolchain!!)
        if (myModule.cargoWorkspace == null) {
            error("Failed to update a test Cargo project")
        }
    }


    override fun runTest() {
        if (toolchain == null) {
            System.err.println("SKIP $name: no Rust toolchain found")
            return
        }
        super.runTest()
    }

    override fun setUp() {
        super.setUp()
        if (toolchain != null) {
             project.rustSettings.data = project.rustSettings.data.copy(toolchain = toolchain)
        }
    }

    override fun tearDown() {
        project.rustSettings.data = project.rustSettings.data.copy(toolchain = null)
        super.tearDown()
    }
}
