/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.MockAdditionalCfgOptions
import org.rust.SkipTestWrapping
import org.rust.ide.colors.RsColor

@SkipTestWrapping
class RsCfgDisabledCodeAnnotatorTest : RsAnnotatorTestBase(RsCfgDisabledCodeAnnotator::class) {
    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(listOf(RsColor.CFG_DISABLED_CODE.testSeverity))
    }

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled function`() = checkHighlighting("""
        #[cfg(intellij_rust)]
        fn foo() {
            let x = 1;
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled function`() = checkHighlighting("""
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[cfg(not(intellij_rust))]
        fn foo() {
            let x = 1;
        }</CFG_DISABLED_CODE>
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled function multiple attrs`() = checkHighlighting("""
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[allow(unused_variables)]
        #[cfg(not(intellij_rust))]
        #[allow(non_camel_case_types)]
        fn foo() {
            let x = 1;
        }</CFG_DISABLED_CODE>
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled async function 2018 edition`() = checkHighlighting("""
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[cfg(not(intellij_rust))]
        async fn foo() {
            let x = 1;
        }</CFG_DISABLED_CODE>
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled try expr 2018 edition`() = checkHighlighting("""
        fn foo() {
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[cfg(not(intellij_rust))]try {
                let x = 1;
            }</CFG_DISABLED_CODE>
        }
    """)

    @BatchMode
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test no highlighting in batch mode`() = checkHighlighting("""
        #[cfg(not(intellij_rust))]
        fn foo() {
            let x = 1;
        }
    """, ignoreExtraHighlighting = false)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled under cfg_attr function`() = checkHighlighting("""
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[cfg_attr(intellij_rust, cfg(not(intellij_rust)))]
        fn foo() {
            let x = 1;
        }</CFG_DISABLED_CODE>
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled under cfg_attr function`() = checkHighlighting("""
        #[cfg_attr(not(intellij_rust), cfg(not(intellij_rust)))]
        fn foo() {
            let x = 1;
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled cfg_attr`() = checkHighlighting("""
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[cfg_attr(not(intellij_rust), deny(unused_variables))]</CFG_DISABLED_CODE>
        fn foo() {
            let x = 1;
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled cfg_attr annotation is not duplicated if the item is disabled`() = checkHighlighting("""
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[cfg(not(intellij_rust))]
        #[cfg_attr(not(intellij_rust), deny(unused_variables))]
        fn foo() {
            let x = 1;
        }</CFG_DISABLED_CODE>
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled code in a macro call`() = checkHighlighting("""
        macro_rules! foo {
            ($ e:expr) => {
                #[cfg(disabled)]
                fn foo() {$ e;}
            };
        }
        fn main() {
            foo!(/*CFG_DISABLED_CODE*/2 + 2/*CFG_DISABLED_CODE**/);
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled code is not highlighted in a macro call if the same code is used as enabled`() = checkHighlighting("""
        macro_rules! foo {
            ($ e:expr) => {
                #[cfg(disabled)]
                fn foo() {$ e;}
            };
        }
        fn main() {
            foo!(2 + 2);
        }
    """)
}
