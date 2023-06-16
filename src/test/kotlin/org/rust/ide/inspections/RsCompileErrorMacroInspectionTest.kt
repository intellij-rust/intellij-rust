/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.*
import org.rust.ide.experiments.RsExperiments

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsCompileErrorMacroInspectionTest : RsInspectionsTestBase(RsCompileErrorMacroInspection::class) {
    fun `test with semicolon`() = checkErrors("""
        /*error descr="the error message 1"*/compile_error!("the error message 1")/*error**/;
    """)

    fun `test with braces`() = checkErrors("""
        /*error descr="the error message 2"*/compile_error! { "the error message 2" }/*error**/
    """)

    fun `test with qualified path`() = checkErrors("""
        /*error descr="the error message 3"*/std::compile_error!("the error message 3")/*error**/;
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test with attribute`() = checkErrors("""
        #[cfg(intellij_rust)]
        /*error descr="the error message"*/std::compile_error!("the error message")/*error**/;
    """)

    fun `test as expression`() = checkErrors("""
        fn main() {
            let _ = /*error descr="the error message"*/compile_error!("the error message")/*error**/;
        }
    """)

    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroAndStdlibRustProjectDescriptor::class)
    fun `test expanded from a macro`() = checkErrors("""
        #[/*error descr="the error message"*/test_proc_macros::attr_err_at_3_first_tokens/*error**/]
        pub fn foo() { }
    """)

    @WithExperimentalFeatures(RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroAndStdlibRustProjectDescriptor::class)
    fun `test expanded from a nested macro`() = checkErrors("""
        #[test_proc_macros::attr_as_is]
        #[/*error descr="the error message"*/test_proc_macros::attr_err_at_3_first_tokens/*error**/]
        pub fn foo() { }
    """)
}
