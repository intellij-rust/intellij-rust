/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.MockCargoFeatures
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.FeatureName
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.lineMarkers.RsLineMarkerProviderTestBase
import org.rust.ide.lineMarkers.invokeNavigationHandler
import org.rust.singleProject
import org.rust.workspaceOrFail

class CargoFeatureLineMarkerProviderTest : RsLineMarkerProviderTestBase() {
    @MockCargoFeatures("foo", "bar", "foobar")
    fun `test simple features`() = doTest("foo", """
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [features]
        foo = []   # - Toggle feature `foo`
        bar = []   # - Toggle feature `bar`
        foobar = ["foo", "bar"]  # - Toggle feature `foobar`
    """)

    @MockCargoFeatures("foo")
    fun `test optional dependency`() = doTest("foo", """
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [dependencies.foo] # - Toggle feature `foo`
        path = "foo"
        optional = true
    """)

    @MockCargoFeatures("foo")
    fun `test optional dependency inline table`() = doTest("foo", """
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [dependencies]
        foo = { path = "foo", optional = true } # - Toggle feature `foo`
    """)

    // TODO the line marker should not be shown for the dependency
    @MockCargoFeatures("foo")
    fun `test optional dependency is not a feature`() = expect<Throwable> {
    doTest("foo", """
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [features]
        foo = ["dep:foo"] # - Toggle feature `foo`

        [dependencies.foo]
        path = "foo"
        optional = true
    """)
    }

    private fun doTest(featureName: FeatureName, @Language("Toml") source: String) {
        doTestByFileTree("Cargo.toml") {
            toml("Cargo.toml", source)
            dir("src") {
                rust("lib.rs", "")
            }
            dir("foo") {
                toml("Cargo.toml", """
                    [package]
                    name = "foo"
                    version = "0.1.0"
                    authors = []
                """)
                dir("src") {
                    rust("lib.rs", "")
                }
            }
        }

        val beforeState = featureState(featureName)
        @Suppress("UNCHECKED_CAST")
        val markerInfo = DaemonCodeAnalyzerImpl.getLineMarkers(myFixture.editor.document, project)
            .first { "Toggle feature `$featureName`" in it.lineMarkerTooltip.orEmpty() } as LineMarkerInfo<PsiElement>
        markerInfo.invokeNavigationHandler(markerInfo.element)
        val afterState = featureState(featureName)

        assertEquals(!beforeState, afterState)
    }

    private fun featureState(featureName: FeatureName): FeatureState {
        val cargoProject = project.cargoProjects.singleProject()
        val pkg = cargoProject.workspaceOrFail().packages.single { it.origin == PackageOrigin.WORKSPACE }
        return pkg.featureState.getValue(featureName)
    }
}
