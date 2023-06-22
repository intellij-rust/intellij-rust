/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.colors.RsColor
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.MacroExpansionManager

// More tests are located in `RsHighlightingAnnotatorTest` (most of those tests are executed
// in both plain and macro context)
class RsMacroExpansionHighlightingPassTest : RsAnnotationTestBase() {

    fun `test attributes inside macro call`() = checkHighlightingInsideMacro("""
        <ATTRIBUTE>#[doc = ""]</ATTRIBUTE>
        fn <FUNCTION>main</FUNCTION>() {
            <ATTRIBUTE>#![crate_type = <STRING>"lib"</STRING>]</ATTRIBUTE>
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled function`() = checkHighlightingInsideMacro("""
        <CFG_DISABLED_CODE>#[cfg(not(intellij_rust))]
        fn foo() {
            let x = 1;
        }</CFG_DISABLED_CODE>
    """)

    @MinRustcVersion("1.46.0")
    @WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test attributes under attribute proc macro`() = checkHighlighting("""
        use <CRATE>test_proc_macros</CRATE>::<FUNCTION>attr_as_is</FUNCTION>;

        <ATTRIBUTE>#[<FUNCTION>attr_as_is</FUNCTION>]</ATTRIBUTE>
        <ATTRIBUTE>#[allow(foo)]</ATTRIBUTE>
        fn <FUNCTION>main</FUNCTION>() {
            <ATTRIBUTE>#![crate_type = <STRING>"lib"</STRING>]</ATTRIBUTE>
        }
    """)

    @MinRustcVersion("1.46.0")
    @WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test attributes under attribute proc macro 2`() = checkHighlighting("""
        use <CRATE>test_proc_macros</CRATE>::<FUNCTION>attr_as_is</FUNCTION>;

        <ATTRIBUTE>#[<FUNCTION>attr_as_is</FUNCTION>]</ATTRIBUTE>
        <ATTRIBUTE>#[<FUNCTION>attr_as_is</FUNCTION>]</ATTRIBUTE>
        <ATTRIBUTE>#[allow(foo)]</ATTRIBUTE>
        fn <FUNCTION>main</FUNCTION>() {
            <ATTRIBUTE>#![crate_type = <STRING>"lib"</STRING>]</ATTRIBUTE>
        }
    """)

    @CheckTestmarkHit(MacroExpansionManager.Testmarks.TooDeepExpansion::class)
    fun `test infinite recursive macro`() = checkHighlighting("""
        macro_rules! foo {
            ($ i:ident) => { foo!($ i) };
        }
        fn main() {
            let _ = foo!(a);
        }
    """)

    private fun checkHighlightingInsideMacro(@Language("Rust") text: String) {
        checkHighlighting("""
            macro_rules! as_is {
                ($($ t:tt)*) => {$($ t)*};
            }
            as_is! {
                $text
            }
        """)
    }

    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(RsColor.values().map(RsColor::testSeverity))
    }

    override fun createAnnotationFixture(): RsAnnotationTestFixture<Unit> =
        RsAnnotationTestFixture(
            this,
            myFixture,
            annotatorClasses = listOf(
                RsHighlightingAnnotator::class,
                RsAttrHighlightingAnnotator::class,
                RsCfgDisabledCodeAnnotator::class
            )
        )
}
