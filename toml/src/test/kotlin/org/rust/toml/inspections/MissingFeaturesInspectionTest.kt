/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import org.junit.runner.RunWith
import org.rust.*
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.ide.annotator.RsAnnotationTestFixture
import org.rust.ide.inspections.RsWithToolchainInspectionTestBase
import org.rust.toml.inspections.MissingFeaturesInspectionTest.Context

@RunWith(RsJUnit4TestRunner::class)
class MissingFeaturesInspectionTest : RsWithToolchainInspectionTestBase<Context>(MissingFeaturesInspection::class) {

    fun `test missing dependency feature`() = doTest(
        pkgWithFeature = "bar",
        featureName = "feature_bar",
        fileToCheck = "foo/src/main.rs"
    ) {
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
                rust("main.rs", """
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
                rust("lib.rs", "")
            }
        }
    }

    fun `test missing required target feature`() = doTest(
        pkgWithFeature = "hello",
        featureName = "feature_hello",
        fileToCheck = "src/main.rs"
    ) {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [[bin]]
            name = "main"
            path = "src/main.rs"
            required-features = ["feature_hello"]

            [features]
            feature_hello = []

            [dependencies]
        """)
        dir("src") {
            rust("main.rs", """
                fn main() {}
            """)
            rust("lib.rs", "")
        }
    }

    fun `test missing dependency feature in manifest`() = doTest(
        pkgWithFeature = "bar",
        featureName = "feature_bar",
        fileToCheck = "foo/Cargo.toml"
    ) {
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
                rust("main.rs", """
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
                rust("lib.rs", "")
            }
        }
    }

    override fun createAnnotationFixture(): RsAnnotationTestFixture<Context> {
        return object : RsAnnotationTestFixture<Context>(
            this@MissingFeaturesInspectionTest,
            myFixture,
            inspectionClasses = listOf(inspectionClass)
        ) {
            override fun configureByFile(file: VirtualFile, context: Context?) {
                require(context != null)
                myFixture.configureFromExistingVirtualFile(file)
                runWriteAction {
                    with(myFixture.editor.document) {
                        setText("<warning descr=\"Missing features: ${context.pkgWithFeature}/${context.featureName}\">$text</warning>")
                    }
                }
            }
        }
    }

    override fun configureProject(fileTree: FileTree, context: Context?): VirtualFile {
        require(context != null)
        val testProject = fileTree.create()

        val cargoProject = project.cargoProjects.singleProject()
        val pkg = cargoProject.workspaceOrFail().packages.find { it.name == context.pkgWithFeature }
            ?: error("Package ${context.pkgWithFeature} not found")

        project.cargoProjects.modifyFeatures(cargoProject, setOf(PackageFeature(pkg, context.featureName)), FeatureState.Disabled)
        return testProject.file(context.fileToCheck)
    }

    private fun doTest(
        pkgWithFeature: String,
        featureName: String,
        fileToCheck: String,
        builder: FileTreeBuilder.() -> Unit
    ) = check(context = Context(pkgWithFeature, featureName, fileToCheck), builder = builder)

    data class Context(val pkgWithFeature: String, val featureName: String, val fileToCheck: String)
}
