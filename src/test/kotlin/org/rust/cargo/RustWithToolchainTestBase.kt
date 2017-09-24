/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import org.rust.FileTree
import org.rust.TestProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.rustSettings
import org.rust.cargo.toolchain.RustToolchain

// This class allows to execute real Cargo during the tests.
// Unlike `RustTestCaseBase` it does not use in-memory temporary VFS
// and instead copies real files.
abstract class RustWithToolchainTestBase : CodeInsightFixtureTestCase<ModuleFixtureBuilder<*>>() {
    open val dataPath: String = ""

    private val toolchain = RustToolchain.suggest()

    protected val cargoProjectDirectory: VirtualFile get() = myFixture.findFileInTempDir(".")

    protected fun FileTree.create(): TestProject =
        create(project, cargoProjectDirectory).apply {
            refreshWorkspace()
        }

    protected fun refreshWorkspace() {
        project.cargoProjects.discoverAndRefreshSync()
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
