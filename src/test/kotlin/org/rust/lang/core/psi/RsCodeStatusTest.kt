/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.intellij.lang.annotations.Language
import org.rust.MockAdditionalCfgOptions
import org.rust.RsTestBase
import org.rust.WithExperimentalFeatures
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.psi.ext.RsCodeStatus
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.getCodeStatus

@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
class RsCodeStatusTest : RsTestBase() {
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled`() = doTest("""
        #[cfg(intellij_rust)]
        mod foo {
            #[allow()]
            mod bar {
                //^ CODE
            }
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg disabled wins 1`() = doTest("""
        #[cfg(not(intellij_rust))]
        mod foo {
            #[attr]
            mod bar {
                //^ CFG_DISABLED
            }
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg disabled wins 2`() = doTest("""
        #[cfg(not(intellij_rust))]
        #[attr]
        mod bar {
            //^ CFG_DISABLED
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg disabled wins 3`() = doTest("""
        #[attr]
        #[cfg(not(intellij_rust))]
        mod bar {
            //^ CFG_DISABLED
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test proc macro wins`() = doTest("""
        #[attr]
        mod foo {
            #[cfg(not(intellij_rust))]
            mod bar {
                //^ ATTR_PROC_MACRO_CALL
            }
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg disabled cfg_attr`() = doTest("""
        #[cfg_attr(not(intellij_rust), attr)]
        mod foo {}                   //^ CFG_DISABLED
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test proc macro argument is enabled`() = doTest("""
        #[attr(arg)]
             //^ CODE
        mod foo {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg disabled wins for proc macro argument`() = doTest("""
        #[cfg(not(intellij_rust))]
        mod foo {
            #[attr(arg)]
                  //^ CFG_DISABLED
            mod foo {}
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test proc macro wins over cfg-unknown`() = doTest("""
        #[cfg(test)]
        mod tests {
            #[attr]
            fn bar () {
               //^ ATTR_PROC_MACRO_CALL
            }
        }
    """)

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val (element, data) = findElementAndDataInEditor<RsElement>()

        val expectedCS = RsCodeStatus.values().find { it.name == data }!!
        val actualCS = element.getCodeStatus(null)
        assertEquals(expectedCS, actualCS)
    }
}
