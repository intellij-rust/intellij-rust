/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.fileTree
import org.rust.openapiext.pathAsPath


class CargoProjectServiceTest : RsWithToolchainTestBase() {
    fun `test finds project for file`() {
        val testProject = fileTree {
            dir("a") {
                toml("Cargo.toml", """
                    [package]
                    name = "a"
                    version = "0.1.0"
                    authors = []
                """)
                dir("src") {
                    rust("lib.rs", "")
                }
            }

            dir("b") {
                toml("Cargo.toml", """
                    [package]
                    name = "b"
                    version = "0.1.0"
                    authors = []

                    [workspace]
                    members = ["../c"]

                    [dependencies]
                    a = { path = "../a" }
                    d = { path = "../d" }
                """)
                dir("src") {
                    rust("lib.rs", "")
                }
            }

            dir("c") {
                toml("Cargo.toml", """
                    [package]
                    name = "c"
                    version = "0.1.0"
                    authors = []
                    workspace = "../b"

                """)
                dir("src") {
                    rust("lib.rs", "")
                }
            }

            dir("d") {
                toml("Cargo.toml", """
                    [package]
                    name = "d"
                    version = "0.1.0"
                    authors = []
                """)
                dir("src") {
                    rust("lib.rs", "")
                }
            }
        }.create(project, cargoProjectDirectory)

        val rootPath = testProject.root.pathAsPath
        val projects = project.cargoProjects.apply {
            attachCargoProjects(
                rootPath.resolve("a/Cargo.toml"),
                rootPath.resolve("b/Cargo.toml"),
                rootPath.resolve("d/Cargo.toml"),
            )
        }

        fun checkFile(relpath: String, projectName: String?) {
            val vFile = testProject.file(relpath)
            val project = projects.findProjectForFile(vFile)
            if (project?.presentableName != projectName) {
                error("Expected $projectName, found $project for $relpath")
            }
        }

        checkFile("a/src/lib.rs", "a")
        checkFile("b/src/lib.rs", "b")
        checkFile("c/src/lib.rs", "b")
        checkFile("d/src/lib.rs", "d")
    }

}
