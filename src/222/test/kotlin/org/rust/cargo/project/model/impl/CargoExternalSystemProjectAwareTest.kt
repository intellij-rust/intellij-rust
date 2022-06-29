/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import org.rust.FileTreeBuilder
import org.rust.TestProject
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.fileTree
import org.rust.lang.core.psi.RsPath
import org.rustSlowTests.cargo.runconfig.waitFinished
import java.io.IOException
import java.util.concurrent.CountDownLatch

class CargoExternalSystemProjectAwareTest : RsWithToolchainTestBase() {

    private val notificationAware get() = AutoImportProjectNotificationAware.getInstance(project)
    private val projectTracker get() = AutoImportProjectTracker.getInstance(project)

    private val cargoSystemId: ExternalSystemProjectId get() = projectTracker.getActivatedProjects()
        .first { it.systemId == CargoExternalSystemProjectAware.CARGO_SYSTEM_ID }

    override fun setUp() {
        super.setUp()
        projectTracker.enableAutoImportInTests()
    }

    fun `test modifications`() {
        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                edition = "2018"

                [workspace]
                members = [ "subproject" ]
            """)
            configs()
            allTargets()

            dir("subproject") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject"
                    version = "0.1.0"
                    edition = "2018"
                """)
                allTargets()
            }
        }.create()

        assertNotificationAware(event = "after project creation")
        // Configs
        testProject.checkFileModification("Cargo.toml", triggered = true)
        testProject.checkFileModification("Cargo.lock", triggered = true)
        testProject.checkFileModification("rust-toolchain", triggered = true)
        testProject.checkFileModification("rust-toolchain.toml", triggered = true)
        testProject.checkFileModification(".cargo/config", triggered = true)
        testProject.checkFileModification(".cargo/config.toml", triggered = true)
        testProject.checkFileModification("subproject/Cargo.toml", triggered = true)
        // Implicit crate roots
        // TODO: it should trigger project model reloading if build script evaluation is enabled
        testProject.checkFileModification("build.rs", triggered = false)
        testProject.checkFileModification("src/lib.rs", triggered = false)
        testProject.checkFileModification("src/main.rs", triggered = false)
        testProject.checkFileModification("src/bin/bin.rs", triggered = false)
        testProject.checkFileModification("examples/example.rs", triggered = false)
        testProject.checkFileModification("benches/bench.rs", triggered = false)
        testProject.checkFileModification("tests/test.rs", triggered = false)
        // TODO: it should trigger project model reloading if build script evaluation is enabled
        testProject.checkFileModification("subproject/build.rs", triggered = false)
        testProject.checkFileModification("subproject/src/lib.rs", triggered = false)
        testProject.checkFileModification("subproject/src/main.rs", triggered = false)
        testProject.checkFileModification("subproject/src/bin/bin.rs", triggered = false)
        testProject.checkFileModification("subproject/examples/example.rs", triggered = false)
        testProject.checkFileModification("subproject/benches/bench.rs", triggered = false)
        testProject.checkFileModification("subproject/tests/test.rs", triggered = false)
        // Regular Rust files
        testProject.checkFileModification("src/foo.rs", triggered = false)
        testProject.checkFileModification("subproject/src/foo.rs", triggered = false)
    }

    fun `test files deletion`() {
        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                edition = "2018"

                [workspace]
                members = [ "subproject" ]
            """)
            configs()
            allTargets()

            dir("subproject") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject"
                    version = "0.1.0"
                    edition = "2018"
                """)
                allTargets()
            }
        }.create()

        assertNotificationAware(event = "after project creation")
        // Configs
        testProject.checkFileDeletion("Cargo.toml", triggered = true)
        testProject.checkFileDeletion("Cargo.lock", triggered = true)
        testProject.checkFileDeletion("rust-toolchain", triggered = true)
        testProject.checkFileDeletion("rust-toolchain.toml", triggered = true)
        testProject.checkFileDeletion(".cargo/config", triggered = true)
        testProject.checkFileDeletion(".cargo/config.toml", triggered = true)
        testProject.checkFileDeletion("subproject/Cargo.toml", triggered = true)
        // Implicit crate roots
        testProject.checkFileDeletion("build.rs", triggered = true)
        testProject.checkFileDeletion("src/main.rs", triggered = true)
        testProject.checkFileDeletion("src/lib.rs", triggered = true)
        testProject.checkFileDeletion("examples/example.rs", triggered = true)
        testProject.checkFileDeletion("benches/bench.rs", triggered = true)
        testProject.checkFileDeletion("tests/test.rs", triggered = true)
        testProject.checkFileDeletion("subproject/build.rs", triggered = true)
        testProject.checkFileDeletion("subproject/src/main.rs", triggered = true)
        testProject.checkFileDeletion("subproject/src/lib.rs", triggered = true)
        testProject.checkFileDeletion("subproject/examples/example.rs", triggered = true)
        testProject.checkFileDeletion("subproject/benches/bench.rs", triggered = true)
        testProject.checkFileDeletion("subproject/tests/test.rs", triggered = true)
        // Regular Rust files
        testProject.checkFileDeletion("src/foo.rs", triggered = false)
        testProject.checkFileDeletion("subproject/src/foo.rs", triggered = false)
    }

    fun `test files creation`() {
        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                edition = "2018"

                [workspace]
                members = [ "subproject" ]
            """)
            noTargets()

            dir("subproject") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject"
                    version = "0.1.0"
                    edition = "2018"
                """)
                noTargets()
            }
        }.create()
        assertNotificationAware(event = "initial project creation")

        // Configs
        testProject.checkFileCreation("rust-toolchain", triggered = true)
        testProject.checkFileCreation("rust-toolchain.toml", triggered = true)
        testProject.checkFileCreation(".cargo/config", triggered = true)
        testProject.checkFileCreation(".cargo/config.toml", triggered = true)
        // Implicit crate roots
        testProject.checkFileCreation("build.rs", triggered = true)
        testProject.checkFileCreation("src/main.rs", triggered = true)
        testProject.checkFileCreation("src/lib.rs", triggered = true)
        testProject.checkFileCreation("examples/example.rs", triggered = true)
        testProject.checkFileCreation("benches/bench.rs", triggered = true)
        testProject.checkFileCreation("tests/test.rs", triggered = true)
        testProject.checkFileCreation("subproject/build.rs", triggered = true)
        testProject.checkFileCreation("subproject/src/main.rs", triggered = true)
        testProject.checkFileCreation("subproject/src/lib.rs", triggered = true)
        testProject.checkFileCreation("subproject/examples/example.rs", triggered = true)
        testProject.checkFileCreation("subproject/benches/bench.rs", triggered = true)
        testProject.checkFileCreation("subproject/tests/test.rs", triggered = true)
        // Regular Rust files
        testProject.checkFileCreation("src/foo.rs", triggered = false)
        testProject.checkFileCreation("subproject/src/foo.rs", triggered = false)
    }

    fun `test reloading`() {
        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                edition = "2018"

                [dependencies]
                #foo = { path = "./foo" }
            """)

            dir("src") {
                rust("main.rs", """
                    extern crate foo;

                    fn main() {
                        foo::hello();
                    }       //^
                """)
            }

            dir("foo") {
                toml("Cargo.toml", """
                    [package]
                    name = "foo"
                    version = "0.1.0"
                    edition = "2018"
                """)

                dir("src") {
                    rust("lib.rs", """
                        pub fn hello() {}
                    """)
                }
            }
        }.create()
        assertNotificationAware(event = "initial project creation")

        testProject.checkReferenceIsResolved<RsPath>("src/main.rs", shouldNotResolve = true)

        val cargoToml = testProject.file("Cargo.toml")
        runWriteAction {
            VfsUtil.saveText(cargoToml, VfsUtil.loadText(cargoToml).replace("#", ""))
        }
        assertNotificationAware(cargoSystemId, event = "modification in Cargo.toml")

        scheduleProjectReload()
        assertNotificationAware(event = "project reloading")

        runWithInvocationEventsDispatching("Failed to resolve the reference") {
            testProject.findElementInFile<RsPath>("src/main.rs").reference?.resolve() != null
        }
    }

    private fun TestProject.checkFileModification(path: String, triggered: Boolean) {
        val file = file(path)
        val initialText = VfsUtil.loadText(file)
        checkModification("modifying of", path, triggered,
            apply = { VfsUtil.saveText(file, "$initialText\nsome text") },
            revert = { VfsUtil.saveText(file, initialText) }
        )
    }

    private fun TestProject.checkFileDeletion(path: String, triggered: Boolean) {
        val file = file(path)
        val initialText = VfsUtil.loadText(file)
        checkModification("removing of ", path, triggered,
            apply = { file.delete(file.fileSystem) },
            revert = { createFile(root, path, initialText) }
        )
    }

    private fun TestProject.checkFileCreation(path: String, triggered: Boolean) {
        checkModification("creation of", path, triggered,
            apply = { createFile(root, path) },
            revert = {
                val file = file(path)
                file.delete(file.fileSystem)
            }
        )
    }

    private fun checkModification(
        eventName: String,
        path: String,
        triggered: Boolean,
        apply: () -> Unit,
        revert: () -> Unit
    ) {
        runWriteAction {
            apply()
        }
        val externalSystems = if (triggered) arrayOf(cargoSystemId) else arrayOf()
        assertNotificationAware(*externalSystems, event = "$eventName $path")

        runWriteAction {
            revert()
        }
        assertNotificationAware(event = "revert $eventName $path")
    }

    private fun scheduleProjectReload() {
        val newDisposable = Disposer.newDisposable()
        val startLatch = CountDownLatch(1)
        val endLatch = CountDownLatch(1)
        project.messageBus.connect(newDisposable).subscribe(
            CargoProjectsService.CARGO_PROJECTS_REFRESH_TOPIC,
            object : CargoProjectsService.CargoProjectsRefreshListener {
                override fun onRefreshStarted() {
                    startLatch.countDown()
                }

                override fun onRefreshFinished(status: CargoProjectsService.CargoRefreshStatus) {
                    endLatch.countDown()
                }
            }
        )

        try {
            projectTracker.scheduleProjectRefresh()

            if (!startLatch.waitFinished(1000)) error("Cargo project reloading hasn't started")
            if (!endLatch.waitFinished(5000)) error("Cargo project reloading hasn't finished")
        } finally {
            Disposer.dispose(newDisposable)
        }
    }

    private fun assertNotificationAware(vararg projects: ExternalSystemProjectId, event: String) {
        val message = if (projects.isEmpty()) "Notification must be expired" else "Notification must be notified"
        assertEquals("$message on $event", projects.toSet(), notificationAware.getProjectsWithNotification())
    }

    @Throws(IOException::class)
    private fun createFile(root: VirtualFile, path: String, text: String = ""): VirtualFile {
        val name = PathUtil.getFileName(path)
        val parentPath = PathUtil.getParentPath(path)
        var parent = root
        if (parentPath.isNotEmpty()) {
            parent = VfsUtil.createDirectoryIfMissing(root, parentPath) ?: error("Failed to create $parentPath directory")
        }
        val file = parent.createChildData(parent.fileSystem, name)
        VfsUtil.saveText(file, text)
        return file
    }

    private fun FileTreeBuilder.allTargets() {
        dir("src") {
            dir("bin") {
                rust("bin.rs", """
                    fn main() {}
                """)
            }
            rust("lib.rs", "")
            rust("main.rs", """
                mod foo;
                fn main() {}
            """)
            rust("foo.rs", "")
        }
        dir("examples") {
            rust("example.rs", """
                fn main() {}
            """)
        }
        dir("benches") {
            rust("bench.rs", "")
        }
        dir("tests") {
            rust("test.rs", "")
        }
        rust("build.rs", """
            fn main() {}
        """)
    }

    private fun FileTreeBuilder.configs() {
        dir(".cargo") {
            // Note, cargo reads only `config` file if both `config` and `config.toml` exist in `.config` dir.
            // But it's OK for tests to have both of them
            toml("config", "")
            toml("config.toml", "")
        }
        // Note, cargo reads only `rust-toolchain` file if both `rust-toolchain` and `rust-toolchain.toml` exist.
        // But it's OK for tests to have both of them
        toml("rust-toolchain", """
            [toolchain]
            channel = "nightly"
        """)
        toml("rust-toolchain.toml", """
            [toolchain]
            channel = "nightly"
        """)
    }

    private fun FileTreeBuilder.noTargets() {
        dir("src") {
            dir("bin") {
                // `cargo metadata` fails if package doesn't contain any target at all
                file("binary.rs")
            }
        }
        dir("examples") {}
        dir("benches") {}
        dir("tests") {}
    }
}
