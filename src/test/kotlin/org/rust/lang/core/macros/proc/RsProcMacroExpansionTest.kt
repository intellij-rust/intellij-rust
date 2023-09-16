/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.proc

import org.rust.ExpandMacros
import org.rust.ProjectDescriptor
import org.rust.WithExperimentalFeatures
import org.rust.WithProcMacroRustProjectDescriptor
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.macros.RsMacroExpansionTestBase

/**
 * See more tests in [RsProcMacroExpanderTest] and in [org.rust.lang.core.resolve.RsProcMacroExpansionResolveTest]
 */
@ExpandMacros(MacroExpansionScope.WORKSPACE)
@WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
class RsProcMacroExpansionTest : RsMacroExpansionTestBase() {
    fun `test item with hardcoded identity attr macro after normal as_is macro`() = doTest("""
        #[test_proc_macros::attr_as_is]
        #[test_proc_macros::attr_hardcoded_as_is]
        fn foo() {}
    """, """
        #[test_proc_macros::attr_hardcoded_as_is]
        fn foo() {}
    """)
}
