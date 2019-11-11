/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.MockAdditionalCfgOptions
import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.colors.RsColor

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
        }
        </CFG_DISABLED_CODE>
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled function multiple attrs`() = checkHighlighting("""
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[allow(unused_variables)]
        #[cfg(not(intellij_rust))]
        #[allow(non_camel_case_types)]
        fn foo() {
            let x = 1;
        }
        </CFG_DISABLED_CODE>
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test disabled async function 2018 edition`() = checkHighlighting("""
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[cfg(not(intellij_rust))]
        async fn foo() {
            let x = 1;
        }
        </CFG_DISABLED_CODE>
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test disabled try expr 2018 edition`() = checkHighlighting("""
        fn foo() {
        <CFG_DISABLED_CODE descr="Conditionally disabled code">#[cfg(not(intellij_rust))]try {
                let x = 1;
            }
        </CFG_DISABLED_CODE>
        }
    """)
}
