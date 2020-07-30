/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.MockAdditionalCfgOptions
import org.rust.ide.colors.RsColor

// More tests are located in `RsHighlightingAnnotatorTest` (most of those tests are executed
// in both plain and macro context)
@ExpandMacros
class RsMacroExpansionHighlightingPassTest : RsAnnotationTestBase() {

    fun `test attributes inside macro call`() = checkHighlightingInsideMacro("""
        <ATTRIBUTE>#</ATTRIBUTE><ATTRIBUTE>[cfg_attr(foo)]</ATTRIBUTE>
        fn <FUNCTION>main</FUNCTION>() {
            <ATTRIBUTE>#![crate_type = <STRING>"lib"</STRING>]</ATTRIBUTE>
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled function`() = checkHighlightingInsideMacro("""
        <CFG_DISABLED_CODE>#</CFG_DISABLED_CODE><CFG_DISABLED_CODE>[cfg(not(intellij_rust))]
        fn foo</CFG_DISABLED_CODE><CFG_DISABLED_CODE>() {
            let x = 1;
        }
        </CFG_DISABLED_CODE>
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

    override fun createAnnotationFixture(): RsAnnotationTestFixture =
        RsAnnotationTestFixture(
            this,
            myFixture,
            annotatorClasses = listOf(RsHighlightingAnnotator::class, RsCfgDisabledCodeAnnotator::class)
        )
}
