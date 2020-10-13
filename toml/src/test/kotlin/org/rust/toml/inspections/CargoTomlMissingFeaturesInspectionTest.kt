/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.InspectionTestUtil
import org.rust.FileTree
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.fileTree

class CargoTomlMissingFeaturesInspectionTest : RsWithToolchainTestBase() {
    fun `test missing dependency feature`() = doTest(
        fileTree {
            toml("Cargo.toml", """
                [workspace]
                members = ["foo", "bar"]
            """)

            dir("foo") {
                toml("Cargo.toml", """
                    [package]
                    name = "foo"
                    version = "0.1.0"
                    authors = []

                    [dependencies]
                    bar = { path = "../bar", features = ["feature_bar"] }
                """)
                dir("src") {
                    file("main.rs", """
                        fn main() {}
                    """)
                }
            }

            dir("bar") {
                toml("Cargo.toml", """
                    [package]
                    name = "bar"
                    version = "0.1.0"
                    authors = []

                    [features]
                    feature_bar = [] # disabled
                """)
                dir("src") {
                    file("lib.rs", "")
                }
            }
        },
        pkgWithFeature = "bar",
        featureName = "feature_bar",
        "foo/Cargo.toml"
    )

    private fun doTest(tree: FileTree, pkgWithFeature: String, featureName: String, vararg filesToCheck: String) {
        tree.create()

        val cargoProject = project.cargoProjects.allProjects.singleOrNull() ?: error("Cargo project is not created")
        val workspace = cargoProject.workspace ?: error("Workspace is not created")
        val pkg = workspace.packages.find { it.name == pkgWithFeature } ?: error("Package $pkgWithFeature not found")

        project.cargoProjects.modifyFeatures(cargoProject, setOf(PackageFeature(pkg, featureName)), FeatureState.Disabled)

        val enabledInspection = InspectionTestUtil.instantiateTool(CargoTomlMissingFeaturesInspection::class.java)
        myFixture.enableInspections(enabledInspection)

        for (fileToCheck in filesToCheck) {
            myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath(fileToCheck)!!)
            val infos = myFixture.doHighlighting(HighlightSeverity.WARNING)
            val info = infos.singleOrNull() ?: error("Not a single annotation in `$fileToCheck`: $infos")
            assertEquals("in `$fileToCheck`", enabledInspection.id, info.inspectionToolId)
        }
    }
}
