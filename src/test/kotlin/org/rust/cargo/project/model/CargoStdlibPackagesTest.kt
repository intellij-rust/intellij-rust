/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model

import org.rust.MinRustcVersion
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.runWithEnabledFeatures
import org.rust.singleProject
import org.rust.workspaceOrFail

@MinRustcVersion("1.41.0")
class CargoStdlibPackagesTest : RsWithToolchainTestBase() {

    fun `test stdlib dependency`() {
        runWithEnabledFeatures(RsExperiments.FETCH_ACTUAL_STDLIB_METADATA) {
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
        }

        val cargoProject = project.cargoProjects.singleProject()
        val workspace = cargoProject.workspaceOrFail()
        val hashbrownPkg = workspace.packages.first { it.name == "hashbrown" }
        assertEquals(PackageOrigin.STDLIB_DEPENDENCY, hashbrownPkg.origin)
        hashbrownPkg.checkFeature("rustc-dep-of-std", FeatureState.Enabled)
        hashbrownPkg.checkFeature("default", FeatureState.Disabled)
    }

    private fun CargoWorkspace.Package.checkFeature(featureName: String, expectedState: FeatureState) {
        val featureState = featureState.getValue(featureName)
        assertEquals("Feature `$featureName` in pakcage `$name` should be in $expectedState", expectedState, featureState)
    }
}
