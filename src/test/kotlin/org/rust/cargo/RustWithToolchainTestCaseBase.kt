package org.rust.cargo

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestCase
import org.rust.cargo.toolchain.RustProjectSettingsService
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.suggestToolchainLocation

// This class allows to execute real Cargo during the tests.
// Unlike `RustTestCaseBase` it does not use in-memory temporary VFS
// and instead copies real files.
abstract class RustWithToolchainTestCaseBase : PlatformTestCase() {
    abstract val dataPath: String

    private val toolchainPath = suggestToolchainLocation()?.absolutePath

    protected fun withProject(projectName: String, action: () -> Unit) {
        val projectDirectory = "$dataPath/$projectName"
        val data = LocalFileSystem.getInstance().findFileByPath(projectDirectory)
            ?: throw RuntimeException("No such directory: $projectDirectory")

        data.refresh(/* asynchronous = */ false, /* recursive = */ true)
        copyDirContentsTo(data, project.baseDir)

        action()
    }

    override fun runTest() {
        if (toolchainPath == null) {
            System.err.println("SKIP $name: no Rust toolchain found")
            return
        }
        super.runTest()
    }

    override fun setUp() {
        super.setUp()
        if (toolchainPath != null) {
            val toolchain = RustToolchain(toolchainPath)
            myModule.project.service<RustProjectSettingsService>().toolchain = toolchain
        }
    }

    override fun tearDown() {
        myModule.project.service<RustProjectSettingsService>().toolchain = null
        super.tearDown()
    }

    override fun createMainModule(): Module? {
        val module = super.createMainModule()
        ModuleRootModificationUtil.addContentRoot(module, project.basePath!!)
        return module
    }
}
