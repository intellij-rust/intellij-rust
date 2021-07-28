/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTree
import org.rust.openapiext.pathAsPath
import org.rust.singleProject

class CargoProjectDeduplicationTest : RsWithToolchainTestBase() {
    fun `test subproject is removed when become a member of attached workspace`() = fileTree {
        dir("root-project") {
            toml("Cargo.toml", """
                #[workspace]
                #members = ["subproject"]

                [package]
                name = "root-project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") { rust("lib.rs", "") }

            dir("subproject") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") { rust("lib.rs", "") }
            }
        }
    }.run {
        create(project, cargoProjectDirectory)
        val rootProjectManifest = cargoProjectDirectory.findFileByRelativePath("root-project/Cargo.toml")!!
        project.testCargoProjects.attachCargoProjects(
            rootProjectManifest.pathAsPath,
            cargoProjectDirectory.pathAsPath.resolve("root-project/subproject/Cargo.toml")
        )
        assertEquals(2, project.testCargoProjects.allProjects.size)
        runWriteAction {
            VfsUtil.saveText(rootProjectManifest, VfsUtil.loadText(rootProjectManifest).replace("#", ""))
        }
        project.testCargoProjects.refreshAllProjectsSync()
        assertEquals(rootProjectManifest.pathAsPath, project.testCargoProjects.singleProject().manifest)
    }

    fun `test 2 subprojects are removed when become members of attached workspace`() = fileTree {
        dir("root-project") {
            toml("__Cargo.toml", """
                [workspace]
                members = ["subproject_1", "subproject_2"]
            """)

            dir("subproject_1") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject_1"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") { rust("lib.rs", "") }
            }

            dir("subproject_2") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject_2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") { rust("lib.rs", "") }
            }
        }
    }.run {
        create(project, cargoProjectDirectory)
        project.testCargoProjects.attachCargoProjects(
            cargoProjectDirectory.pathAsPath.resolve("root-project/subproject_1/Cargo.toml"),
            cargoProjectDirectory.pathAsPath.resolve("root-project/subproject_2/Cargo.toml")
        )
        assertEquals(2, project.testCargoProjects.allProjects.size)
        val rootProjectManifest = cargoProjectDirectory.findFileByRelativePath("root-project/__Cargo.toml")!!
        runWriteAction {
            rootProjectManifest.rename(null, "Cargo.toml")
        }
        project.testCargoProjects.attachCargoProject(rootProjectManifest.pathAsPath)
        assertEquals(rootProjectManifest.pathAsPath, project.testCargoProjects.singleProject().manifest)
    }

    fun `test one subproject is removed and one is excluded when become members of attached workspace`() = fileTree {
        dir("root-project") {
            toml("Cargo.toml", """
                #[workspace]
                #members = ["subproject_1"]
                #exclude = ["subproject_2"]

                [package]
                name = "root-project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") { rust("lib.rs", "") }

            dir("subproject_1") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject_1"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") { rust("lib.rs", "") }
            }

            dir("subproject_2") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject_2"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") { rust("lib.rs", "") }
            }
        }
    }.run {
        create(project, cargoProjectDirectory)
        val rootProjectManifest = cargoProjectDirectory.findFileByRelativePath("root-project/Cargo.toml")!!
        val includedProjectManifest = cargoProjectDirectory.pathAsPath.resolve("root-project/subproject_1/Cargo.toml")
        val excludedProjectManifest = cargoProjectDirectory.pathAsPath.resolve("root-project/subproject_2/Cargo.toml")
        project.testCargoProjects.attachCargoProjects(includedProjectManifest, excludedProjectManifest)
        assertEquals(2, project.testCargoProjects.allProjects.size)
        runWriteAction {
            VfsUtil.saveText(rootProjectManifest, VfsUtil.loadText(rootProjectManifest).replace("#", ""))
        }
        project.testCargoProjects.attachCargoProject(rootProjectManifest.pathAsPath)
        assertEquals(
            listOf(
                rootProjectManifest.pathAsPath,
                excludedProjectManifest
            ).sorted(),
            project.testCargoProjects.allProjects.map { it.manifest }.sorted()
        )
    }

    fun `test subproject is not removed is added as an external dependency to another project`() = fileTree {
        dir("root-project") {
            toml("Cargo.toml", """
                [package]
                name = "root-project"
                version = "0.1.0"
                authors = []

                #[dependencies]
                #subproject = { path = "./subproject" }
            """)

            dir("src") { rust("lib.rs", "") }

            dir("subproject") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") { rust("lib.rs", "") }
            }
        }
    }.run {
        create(project, cargoProjectDirectory)
        val rootProjectManifest = cargoProjectDirectory.findFileByRelativePath("root-project/Cargo.toml")!!
        project.testCargoProjects.attachCargoProjects(
            rootProjectManifest.pathAsPath,
            cargoProjectDirectory.pathAsPath.resolve("root-project/subproject/Cargo.toml")
        )
        assertEquals(2, project.testCargoProjects.allProjects.size)
        runWriteAction {
            VfsUtil.saveText(rootProjectManifest, VfsUtil.loadText(rootProjectManifest).replace("#", ""))
        }
        project.testCargoProjects.refreshAllProjectsSync()
        check(project.testCargoProjects.allProjects.size == 2)
        check(project.testCargoProjects.allProjects.all { it.workspace != null })
    }
}
