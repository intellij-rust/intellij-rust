/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.expansion

@ExpandMacros
class RsMacroCallUnderCfgTest : RsTestBase() {
    @MockCargoFeatures("feature_foo")
    fun `test simple`() = checkResolvedOnlyWhenFeatureIsEnabled("feature_foo", """
        macro_rules! foo {
            () => {
                fn foobar() {}
            };
        }

        #[cfg(feature = "feature_foo")]
        foo! {}

        fn main() {
            foobar();
            //^
        }
    """)

    @MockCargoFeatures("feature_foo")
    fun `test nested macro call`() = checkResolvedOnlyWhenFeatureIsEnabled("feature_foo", """
        macro_rules! foo {
            () => {
                fn foobar() {}
            };
        }

        macro_rules! bar {
            () => {
                #[cfg(feature = "feature_foo")]
                foo! {}
            };
        }

        bar! {}

        fn main() {
            foobar();
            //^
        }
    """)

    @MockCargoFeatures("feature_foo")
    fun `test nested inline mod`() = checkResolvedOnlyWhenFeatureIsEnabled("feature_foo", """
        macro_rules! foo {
            () => {
                pub fn foobar() {}
            };
        }

        macro_rules! bar {
            () => {
                #[cfg(feature = "feature_foo")]
                mod foo {
                    foo! {}
                }
            };
        }

        bar! {}

        fn main() {
            foo::foobar();
               //^
        }
    """)

    @MockCargoFeatures("feature_foo")
    fun `test macro call in cfg disabled nested mod`() = checkResolvedOnlyWhenFeatureIsEnabledByTree("feature_foo", """
    //- foo.rs
        foo! {}
    //- main.rs
        macro_rules! foo {
            () => {
                pub fn foobar() {}
            };
        }

        macro_rules! bar {
            () => {
                #[cfg(feature = "feature_foo")]
                mod foo;
            };
        }

        bar! {}

        fn main() {
            foo::foobar();
               //^
        }
    """)

    @MockCargoFeatures("feature_foo")
    fun `test nested macro call in cfg disabled mod`() = checkResolvedOnlyWhenFeatureIsEnabledByTree("feature_foo", """
    //- foo.rs
        bar! {}
    //- main.rs
        macro_rules! foo {
            () => {
                pub fn foobar() {}
            };
        }

        macro_rules! bar {
            () => {
                foo! {}
            };
        }

        #[cfg(feature = "feature_foo")]
        mod foo;

        fn main() {
            foo::foobar();
               //^
        }
    """)

    private fun checkResolvedOnlyWhenFeatureIsEnabled(feature: String, @Language("Rust") code: String) {
        checkResolvedOnlyWhenFeatureIsEnabledInner(feature) {
            InlineFile(code)
        }
    }

    private fun checkResolvedOnlyWhenFeatureIsEnabledByTree(feature: String, @Language("Rust") code: String) {
        checkResolvedOnlyWhenFeatureIsEnabledInner(feature) {
            fileTreeFromText(code).createAndOpenFileWithCaretMarker()
        }
    }

    private fun checkResolvedOnlyWhenFeatureIsEnabledInner(
        feature: String,
        init: () -> Unit
    ) {
        init()

        // Enabled -> Disabled -> Enabled
        run {
            val (refElement, _, _) = findElementWithDataAndOffsetInEditor<RsReferenceElement>("^")
            val resolved = { refElement.reference!!.resolve() != null }

            check(resolved())

            setCargoFeature(feature, FeatureState.Disabled)
            check(!resolved())
            check(collectMacroCallsRecursively(myFixture.file).any { it.expansion == null })

            setCargoFeature(feature, FeatureState.Enabled)
            check(resolved())
        }

        runWriteAction { myFixture.tempDirFixture.getFile(".")!!.children.forEach { it.delete(null) } }
        setCargoFeature(feature, FeatureState.Disabled)
        init()
        runWriteAction { project.macroExpansionManager.reexpand() }

        // Disabled -> Enabled
        run {
            val (refElement, _, _) = findElementWithDataAndOffsetInEditor<RsReferenceElement>("^")
            val resolved = { refElement.reference!!.resolve() != null }

            check(!resolved())
            check(collectMacroCallsRecursively(myFixture.file).any { it.expansion == null })

            setCargoFeature(feature, FeatureState.Enabled)
            check(resolved())
        }
    }

    private fun collectMacroCallsRecursively(psi: PsiElement): List<RsMacroCall> {
        return PsiTreeUtil.findChildrenOfAnyType(psi, RsMacroCall::class.java, RsModDeclItem::class.java)
            .flatMap {
                when (it) {
                    is RsMacroCall -> listOf(it) + (it.expansion?.file?.let(::collectMacroCallsRecursively).orEmpty())
                    is RsModDeclItem -> (it.reference.resolve() as? RsFile)?.let(::collectMacroCallsRecursively).orEmpty()
                    else -> error("impossible")
                }
            }
    }

    private fun setCargoFeature(feature: String, newState: FeatureState) {
        val pkg = project.cargoProjects.singleWorkspace().packages.single()
        project.cargoProjects.modifyFeatures(project.cargoProjects.singleProject(), setOf(PackageFeature(pkg, feature)), newState)
    }
}
