/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.VirtualFile
import org.rust.cargo.RsWithToolchainTestBase

class RsContentRootsTest : RsWithToolchainTestBase() {

    fun `test project with subproject`() {
        val project = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [workspace]
                members = ["subproject"]
            """)
            dir("src") {
                rust("main.rs", "")
            }
            dir("examples") {}
            dir("tests") {}
            dir("benches") {}
            dir("target") {}

            dir("subproject") {
                toml("Cargo.toml", """
                    [package]
                    name = "subproject"
                    version = "0.1.0"
                    authors = []
                """)
                dir("src") {
                    rust("main.rs", "")
                }
                dir("examples") {}
                dir("tests") {}
                dir("benches") {}
                dir("target") {}
            }
        }

        val projectFolders = listOf(
            ProjectFolder.Source(project.file("src"), false),
            ProjectFolder.Source(project.file("examples"), false),
            ProjectFolder.Source(project.file("tests"), true),
            ProjectFolder.Source(project.file("benches"), true),
            ProjectFolder.Excluded(project.file("target")),
            ProjectFolder.Source(project.file("subproject/src"), false),
            ProjectFolder.Source(project.file("subproject/examples"), false),
            ProjectFolder.Source(project.file("subproject/tests"), true),
            ProjectFolder.Source(project.file("subproject/benches"), true),
            ProjectFolder.Excluded(project.file("subproject/target"))
        )

        check(projectFolders)
    }

    fun `test do not add non existing roots`() {
        val project = buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("main.rs", "")
            }
            dir("tests") {}
            dir("target") {}
        }

        val projectFolders = listOf(
            ProjectFolder.Source(project.file("src"), false),
            ProjectFolder.Source(project.file("tests"), true),
            ProjectFolder.Excluded(project.file("target"))
        )

        check(projectFolders)
    }


    fun `test workspace without root package`() {
        val project = buildProject {
            toml("Cargo.toml", """
                [workspace]
                members = ["package1", "package2"]
            """)
            dir("target") {}

            dir("package1") {
                toml("Cargo.toml", """
                    [package]
                    name = "package1"
                    version = "0.1.0"
                    authors = []
                """)
                dir("src") {
                    rust("main.rs", "")
                }
                dir("examples") {}
                dir("tests") {}
                dir("benches") {}
                dir("target") {}
            }
            dir("package2") {
                toml("Cargo.toml", """
                    [package]
                    name = "package2"
                    version = "0.1.0"
                    authors = []
                """)
                dir("src") {
                    rust("main.rs", "")
                }
                dir("examples") {}
                dir("tests") {}
                dir("benches") {}
                dir("target") {}
            }
        }

        val projectFolders = listOf(
            ProjectFolder.Excluded(project.file("target")),

            ProjectFolder.Source(project.file("package1/src"), false),
            ProjectFolder.Source(project.file("package1/examples"), false),
            ProjectFolder.Source(project.file("package1/tests"), true),
            ProjectFolder.Source(project.file("package1/benches"), true),
            ProjectFolder.Excluded(project.file("package1/target")),

            ProjectFolder.Source(project.file("package2/src"), false),
            ProjectFolder.Source(project.file("package2/examples"), false),
            ProjectFolder.Source(project.file("package2/tests"), true),
            ProjectFolder.Source(project.file("package2/benches"), true),
            ProjectFolder.Excluded(project.file("package2/target"))
        )

        check(projectFolders)
    }


    private fun check(projectFolders: List<ProjectFolder>) {
        ModuleRootModificationUtil.updateModel(myModule) { model ->
            val contentEntry = model.contentEntries.firstOrNull() ?: error("Can't find any content entry")
            val sourceFiles = contentEntry.sourceFolders.associateBy {
                it.file ?: error("Can't find file with `${it.url}`")
            }
            val excludedFiles = contentEntry.excludeFolders.associateBy {
                it.file ?: error("Can't find file with `${it.url}`")
            }

            for (projectFolder in projectFolders) {
                when (projectFolder) {
                    is ProjectFolder.Source -> {
                        val sourceFile = sourceFiles[projectFolder.file]
                            ?: error("Can't find `${projectFolder.file}` folder in source folders")
                        check(sourceFile.isTestSource == projectFolder.isTest)
                    }
                    is ProjectFolder.Excluded -> {
                        if (excludedFiles[projectFolder.file] == null) {
                            error("Can't find `${projectFolder.file}` folder in excluded folders")
                        }
                    }
                }
            }
        }
    }

    private sealed class ProjectFolder {
        data class Source(val file: VirtualFile, val isTest: Boolean) : ProjectFolder()
        data class Excluded(val file: VirtualFile) : ProjectFolder()
    }
}
