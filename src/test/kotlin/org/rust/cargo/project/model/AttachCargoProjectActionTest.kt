/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.ui.UIUtil
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTree
import org.rust.openapiext.pathAsPath
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AttachCargoProjectActionTest : RsWithToolchainTestBase() {

    private val tempDirFixture = TempDirTestFixtureImpl()

    private val cargoProjectSupplier: FileTreeBuilder.() -> Unit = {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {
                    println!("Hello, world!");
                }
            """)
        }
    }

    override fun setUp() {
        super.setUp()
        tempDirFixture.setUp()
    }

    override fun tearDown() {
        tempDirFixture.tearDown()
        super.tearDown()
    }

    fun `test attach cargo project via root directory`() {
        val testProject = buildProject {
            dir("dir", cargoProjectSupplier)
        }

        val rootDir = testProject.root.findChild("dir")!!
        testAction(rootDir, true)
        val cargoProject = project.cargoProjects.allProjects.find { it.rootDir == rootDir }
        assertNotNull("Failed to attach project in `$rootDir`", cargoProject)
    }

    fun `test attach cargo project via cargo toml`() {
        val testProject = buildProject {
            dir("dir", cargoProjectSupplier)
        }

        val cargoToml = testProject.root.findFileByRelativePath("dir/Cargo.toml")!!
        testAction(cargoToml, true)
        val cargoProject = project.cargoProjects.allProjects.find { it.manifest == cargoToml.pathAsPath }
        assertNotNull("Failed to attach project via `$cargoToml` file", cargoProject)
    }

    fun `test no action for cargo toml of existing cargo project`() {
        val testProject = buildProject(cargoProjectSupplier)
        val cargoToml = testProject.root.findFileByRelativePath("Cargo.toml")!!
        testAction(cargoToml, false)
    }

    fun `test no action for cargo toml of existing package in cargo project`() {
        val testProject = buildProject {
            toml("Cargo.toml", """
                [workspace]
                members = [ "foo" ]
            """)

            dir("foo", cargoProjectSupplier)
        }

        val cargoToml = testProject.root.findFileByRelativePath("foo/Cargo.toml")!!
        testAction(cargoToml, false)
    }

    fun `test no action for cargo toml outside of project`() {
        val libraryProject = fileTree(cargoProjectSupplier).create(project, tempDirFixture.getFile("")!!)
        val cargoToml = libraryProject.root.findFileByRelativePath("Cargo.toml")!!
        testAction(cargoToml, false)
    }

    private fun testAction(file: VirtualFile, shouldBeEnabled: Boolean) {
        val context = MapDataContext().apply {
            put(PlatformDataKeys.PROJECT, project)
            put(PlatformDataKeys.VIRTUAL_FILE, file)
        }
        val testEvent = TestActionEvent(context)
        val action = AttachCargoProjectAction()
        action.beforeActionPerformedUpdate(testEvent)
        assertEquals(shouldBeEnabled, testEvent.presentation.isEnabledAndVisible)
        if (shouldBeEnabled) {
            val latch = CountDownLatch(1)
            project.messageBus.connect().subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
                override fun cargoProjectsUpdated(projects: Collection<CargoProject>) {
                    latch.countDown()
                }
            })

            action.actionPerformed(testEvent)

            for (i in 1..20) {
                UIUtil.dispatchAllInvocationEvents()
                if (latch.await(50, TimeUnit.MILLISECONDS)) break
            }
        }
    }
}
