/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsCrateTypeAttrCompletionProviderTest : RsCompletionTestBase() {
    fun `test basic completion`() = doSingleCompletion("""
        #![crate_type = "pro<caret>"]
    """, """
        #![crate_type = "proc-macro<caret>"]
    """)

    fun `test has popular types`() = checkContainsCompletion(listOf("bin", "lib"), """
        #![crate_type = "<caret>"]
    """)

    fun `test completion when argument in configuration`() = doSingleCompletion("""
        #![cfg_attr(unix, crate_type = "bi<caret>")]
    """, """
        #![cfg_attr(unix, crate_type = "bin<caret>")]
    """)

    fun `test no completion when outer`() = checkNoCompletion("""
        #[crate_type = "<caret>"]
        fn foo() {}
    """)

    fun `test no completion when not root`() = checkNoCompletion("""
        #![not(crate_type = "<caret>")]
    """)

    fun `test no completion when wrong key`() = checkNoCompletion("""
        #![something_type = "<caret>"]
    """)
}
