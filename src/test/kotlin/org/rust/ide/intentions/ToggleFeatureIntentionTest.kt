/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.experiments.RsExperiments

class ToggleFeatureIntentionTest : RsIntentionTestBase(ToggleFeatureIntention::class) {

    override val previewExpected: Boolean get() = false

    @WithoutExperimentalFeatures(RsExperiments.PROC_MACROS, RsExperiments.ATTR_PROC_MACROS)
    @SkipTestWrapping // TODO fix `PsiElement.findExpansionElementsNonRecursive` for `cfg_attr` condition
    @MockCargoFeatures("foo")
    fun `test availability range`() = checkAvailableInSelectionOnly("""
        #[cfg(<selection>feature = "foo"</selection>)]
        #[cfg(not(<selection>feature = "foo"</selection>))]
        #[cfg_attr(<selection>feature = "foo"</selection>, foobar)]
        #[cfg_attr(foo, cfg_attr(<selection>feature = "foo"</selection>, foobar))]
        #[cfg_attr(foobar, feature = "foo")]
        #[foobar(feature = "foo")]
        fn foo() {}
    """)

    fun `test unknown feature`() = doUnavailableTest("""
        #[cfg(feature = "bar"/*caret*/)]
        fn foo() {}
    """)

    @MockCargoFeatures("foo")
    fun `test enable`() = doTest("""
        #[cfg(feature = "foo"/*caret*/)]
        fn foo() {}
    """, "foo", FeatureState.Disabled)

    @MockCargoFeatures("foo")
    fun `test disable`() = doTest("""
        #[cfg(feature = "foo"/*caret*/)]
        fn foo() {}
    """, "foo", FeatureState.Enabled)

    private fun doTest(@Language("Rust") code: String, featureName: String, initialState: FeatureState) {
        val cargoProject = project.cargoProjects.singleProject()
        val pkg = cargoProject.workspaceOrFail().packages.single { it.origin == PackageOrigin.WORKSPACE }
        val feature = pkg.features.find { it.name == featureName } ?: error("Feature $featureName not found in test")
        project.cargoProjects.modifyFeatures(cargoProject, setOf(feature), initialState)

        InlineFile(code.trimIndent()).withCaret()
        val previewChecker = launchAction()

        val cargoProjectRefreshed = project.cargoProjects.singleProject()
        val pkgRefreshed = cargoProjectRefreshed.workspaceOrFail().packages.single { it.origin == PackageOrigin.WORKSPACE }

        assertEquals(!initialState, pkgRefreshed.featureState[featureName])
        previewChecker.checkPreview()
    }
}
