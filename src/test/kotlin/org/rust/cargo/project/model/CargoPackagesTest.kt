/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.openapi.util.SystemInfo
import org.rust.FileTreeBuilder
import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.singleProject
import org.rust.workspaceOrFail

class CargoPackagesTest : RsWithToolchainTestBase() {

    fun `test target specific dependencies`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []

                [dependencies]
                common_package = { path = "common_package" }

                [target.'cfg(unix)'.dependencies]
                unix_package = { path = "unix_package" }

                [target.'cfg(windows)'.dependencies]
                windows_package = { path = "windows_package" }
            """)

            dir("src") {
                rust("lib.rs", "")
            }

            dependencyPackage("common_package")
            dependencyPackage("unix_package")
            dependencyPackage("windows_package")
        }

        val workspace = project.cargoProjects.singleProject().workspaceOrFail()

        workspace.checkPackage("common_package", shouldExist = true)
        workspace.checkPackage("unix_package", shouldExist = SystemInfo.isUnix)
        workspace.checkPackage("windows_package", shouldExist = SystemInfo.isWindows)
    }

    @MinRustcVersion("1.53.0")
    fun `test target specific dependencies with build target`() {
        buildProject {
            dir(".cargo") {
                toml("config.toml", """
                    [build]
                    target = "x86_64-pc-windows-msvc"
                """)
            }
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []

                [dependencies]
                common_package = { path = "common_package" }

                [target.'cfg(unix)'.dependencies]
                unix_package = { path = "unix_package" }

                [target.'cfg(windows)'.dependencies]
                windows_package = { path = "windows_package" }
            """)

            dir("src") {
                rust("lib.rs", "")
            }

            dependencyPackage("common_package")
            dependencyPackage("unix_package")
            dependencyPackage("windows_package")
        }

        val workspace = project.cargoProjects.singleProject().workspaceOrFail()

        workspace.checkPackage("common_package", shouldExist = true)
        workspace.checkPackage("unix_package", shouldExist = false)
        workspace.checkPackage("windows_package", shouldExist = true)
    }

    private fun FileTreeBuilder.dependencyPackage(name: String) {
        dir(name) {
            toml("Cargo.toml", """
                [package]
                name = "$name"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("lib.rs", "")
            }
        }
    }

    private fun CargoWorkspace.checkPackage(name: String, shouldExist: Boolean) {
        val pkg = packages.find { it.name == name }
        if (shouldExist) {
            assertNotNull("Can't find `$name` in workspace", pkg)
        } else {
            assertNull("Workspace shouldn't contain `$name` package", pkg)
        }
    }
}
