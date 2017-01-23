package org.rust.cargo

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestCase
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.RustToolchain

// This class allows to execute real Cargo during the tests.
// Unlike `RustTestCaseBase` it does not use in-memory temporary VFS
// and instead copies real files.
abstract class RustWithToolchainTestBase : PlatformTestCase() {
    abstract val dataPath: String

    private val toolchain = RustToolchain.suggest()

    protected fun withProject(projectName: String, action: () -> Unit) {
        val projectDirectory = "$dataPath/$projectName"
        val data = LocalFileSystem.getInstance().findFileByPath(projectDirectory)
            ?: throw RuntimeException("No such directory: $projectDirectory")

        data.refresh(/* asynchronous = */ false, /* recursive = */ true)
        copyDirContentsTo(data, project.baseDir)

        action()
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
            project.rustSettings.toolchain = toolchain
        }
    }

    override fun tearDown() {
        project.rustSettings.toolchain = null
        super.tearDown()
    }

    override fun createMainModule(): Module? {
        val module = super.createMainModule()
        ModuleRootModificationUtil.addContentRoot(module, project.basePath!!)
        return module
    }
}
