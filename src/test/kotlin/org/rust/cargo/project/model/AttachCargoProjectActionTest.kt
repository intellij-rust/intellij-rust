/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.ui.UIUtil
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.toolwindow.CargoToolWindow
import org.rust.fileTree
import org.rust.ide.notifications.RsEditorNotificationPanel
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
        testAction(ActionPlaces.PROJECT_VIEW_POPUP, true, contextFile = rootDir)
        val cargoProject = project.cargoProjects.allProjects.find { it.rootDir == rootDir }
        assertNotNull("Failed to attach project in `$rootDir`", cargoProject)
    }

    fun `test attach cargo project via cargo toml`() {
        val testProject = buildProject {
            dir("dir", cargoProjectSupplier)
        }

        val cargoToml = testProject.root.findFileByRelativePath("dir/Cargo.toml")!!
        testAction(ActionPlaces.PROJECT_VIEW_POPUP, true, contextFile = cargoToml)
        val cargoProject = project.cargoProjects.allProjects.find { it.manifest == cargoToml.pathAsPath }
        assertNotNull("Failed to attach project via `$cargoToml` file", cargoProject)
    }

    fun `test no action for cargo toml of existing cargo project`() {
        val testProject = buildProject(cargoProjectSupplier)
        val cargoToml = testProject.root.findFileByRelativePath("Cargo.toml")!!
        testAction(ActionPlaces.PROJECT_VIEW_POPUP, false, contextFile = cargoToml)
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
        testAction(ActionPlaces.PROJECT_VIEW_POPUP, false, contextFile = cargoToml)
    }

    fun `test no action for cargo toml outside of project`() {
        val libraryProject = fileTree(cargoProjectSupplier).create(project, tempDirFixture.getFile("")!!)
        val cargoToml = libraryProject.root.findFileByRelativePath("Cargo.toml")!!
        testAction(ActionPlaces.PROJECT_VIEW_POPUP, false, contextFile = cargoToml)
    }

    fun `test action is always available from cargo tool window`() {
        val testProject = buildProject {
            dir("dir", cargoProjectSupplier)
        }

        val cargoToml = testProject.root.findFileByRelativePath("dir/Cargo.toml")!!
        testAction(CargoToolWindow.CARGO_TOOLBAR_PLACE, true, contextFile = null, mockChosenFile = cargoToml)
        val cargoProject = project.cargoProjects.allProjects.find { it.manifest == cargoToml.pathAsPath }
        assertNotNull("Failed to attach project via `$cargoToml` file", cargoProject)
    }

    fun `test action is available from editor notification 1`() {
        val testProject = buildProject {
            dir("dir", cargoProjectSupplier)
        }

        val srcFile = testProject.root.findFileByRelativePath("dir/src/main.rs")!!
        val cargoToml = testProject.root.findFileByRelativePath("dir/Cargo.toml")!!
        testAction(RsEditorNotificationPanel.NOTIFICATION_PANEL_PLACE, true, contextFile = srcFile, mockChosenFile = cargoToml)
        val cargoProject = project.cargoProjects.allProjects.find { it.manifest == cargoToml.pathAsPath }
        assertNotNull("Failed to attach project via `$srcFile` file", cargoProject)
    }

    fun `test action is available from editor notification 2`() {
        val testProject = buildProject {
            dir("dir", cargoProjectSupplier)
        }

        val cargoToml = testProject.root.findFileByRelativePath("dir/Cargo.toml")!!
        testAction(RsEditorNotificationPanel.NOTIFICATION_PANEL_PLACE, true, contextFile = cargoToml)
        val cargoProject = project.cargoProjects.allProjects.find { it.manifest == cargoToml.pathAsPath }
        assertNotNull("Failed to attach project via `$cargoToml` file", cargoProject)
    }

    private fun testAction(place: String, shouldBeEnabled: Boolean, contextFile: VirtualFile? = null, mockChosenFile: VirtualFile? = null) {
        val context = MapDataContext().apply {
            put(PlatformDataKeys.PROJECT, project)
            put(PlatformDataKeys.VIRTUAL_FILE, contextFile)
            put(AttachCargoProjectAction.MOCK_CHOSEN_FILE_KEY, mockChosenFile)
        }
        val testEvent = TestActionEvent(context, place)
        val action = AttachCargoProjectAction()
        action.beforeActionPerformedUpdate(testEvent)
        assertEquals(shouldBeEnabled, testEvent.presentation.isEnabledAndVisible)
        if (shouldBeEnabled) {
            val latch = CountDownLatch(1)
            project.messageBus.connect().subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
                override fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>) {
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

    private class TestActionEvent(
        dataContext: DataContext,
        private val testPlace: String
    ) : com.intellij.testFramework.TestActionEvent(dataContext) {
        override fun getPlace(): String = testPlace
    }
}
