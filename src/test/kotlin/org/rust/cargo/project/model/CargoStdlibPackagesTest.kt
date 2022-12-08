/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import com.intellij.util.io.delete
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.impl.testCargoProjects
import org.rust.cargo.project.workspace.*
import org.rust.singleProject
import org.rust.workspaceOrFail

class CargoStdlibPackagesTest : RsWithToolchainTestBase() {

    override val fetchActualStdlibMetadata: Boolean get() = true

    fun `test stdlib dependency`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", "")
            }
        }

        val cargoProject = project.cargoProjects.singleProject()
        val workspace = cargoProject.workspaceOrFail()
        val hashbrownPkg = workspace.packages.first { it.name == HASHBROWN }
        assertEquals(PackageOrigin.STDLIB_DEPENDENCY, hashbrownPkg.origin)
        hashbrownPkg.checkFeature("rustc-dep-of-std", FeatureState.Enabled)
        hashbrownPkg.checkFeature("default", FeatureState.Disabled)

        for (pkgName in TARGET_SPECIFIC_DEPENDENCIES) {
            val pkg = workspace.packages.find { it.name == pkgName }
            assertNull("$pkgName shouldn't be in stdlib dependencies because it's target-specific", pkg)
        }
    }

    fun `test target specific dependencies with custom build target`() {
        buildProject {
            dir(".cargo") {
                toml("config", """
                    [build]
                    target = "custom-target.json"
                """)
            }
            file("custom-target.json", """
                {
                    "llvm-target": "aarch64-unknown-none",
                    "data-layout": "e-m:e-i64:64-f80:128-n8:16:32:64-S128",
                    "arch": "aarch64",
                    "target-endian": "little",
                    "target-pointer-width": "64",
                    "target-c-int-width": "32",
                    "os": "none",
                    "executables": true,
                    "linker-flavor": "ld.lld",
                    "linker": "rust-lld",
                    "panic-strategy": "abort",
                    "disable-redzone": true,
                    "features": "-mmx,-sse,+soft-float"
                }
            """)
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", "")
            }
        }

        val workspace = project.cargoProjects.singleProject().workspaceOrFail()
        val hashbrownPkg = workspace.packages.first { it.name == HASHBROWN }
        assertEquals(PackageOrigin.STDLIB_DEPENDENCY, hashbrownPkg.origin)
    }

    fun `test recover corrupted stdlib dependency directory`() {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "sandbox"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", "")
            }
        }

        val cargoProjectService = project.testCargoProjects
        val cargoProject = cargoProjectService.singleProject()
        val version = cargoProject.rustcInfo?.version ?: error("")
        val srcDir = rustupFixture.stdlib?.let { StandardLibrary.findSrcDir(it) } ?: error("")

        val path = StdlibDataFetcher.stdlibVendorDir(srcDir, version)
        // Corrupt vendor directory
        path.resolve(HASHBROWN).delete(true)

        cargoProjectService.refreshAllProjectsSync()

        val hashbrownPkg = cargoProjectService
            .singleProject()
            .workspaceOrFail()
            .packages
            .firstOrNull { it.name == HASHBROWN }
        assertNotNull("Stdlib must contain `$HASHBROWN` package", hashbrownPkg)

    }

    private fun CargoWorkspace.Package.checkFeature(featureName: String, expectedState: FeatureState) {
        val featureState = featureState.getValue(featureName)
        assertEquals("Feature `$featureName` in package `$name` should be in $expectedState", expectedState, featureState)
    }

    companion object {
        private const val HASHBROWN = "hashbrown"

        // Some stdlib dependencies for non default (from IDE point of view) build targets
        private val TARGET_SPECIFIC_DEPENDENCIES = listOf("wasi", "hermit-abi", "dlmalloc", "fortanix-sgx-abi")
    }
}
