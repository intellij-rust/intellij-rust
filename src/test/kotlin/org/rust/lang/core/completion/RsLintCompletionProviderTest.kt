/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsLintCompletionProviderTest : RsAttributeCompletionTestBase() {
    fun `test complete inner attribute`() = doSingleAttributeCompletion("""
        #![allow(unused_var/*caret*/)]
    """, """
        #![allow(unused_variables/*caret*/)]
    """)

    fun `test complete outer attribute`() = doSingleAttributeCompletion("""
        #[allow(unused_var/*caret*/)]
        fn foo() {}
    """, """
        #[allow(unused_variables/*caret*/)]
        fn foo() {}
    """)

    fun `test complete clippy group at root`() = doSingleAttributeCompletion("""
        #[allow(clip/*caret*/)]
        fn foo() {}
    """, """
        #[allow(clippy::/*caret*/)]
        fn foo() {}
    """)

    fun `test do not complete clippy lints at root`() = checkNotContainsCompletion("borrow_interior_mutable_const", """
        #[allow(borr/*caret*/)]
        fn foo() {}
    """)

    fun `test complete inside clippy`() = checkContainsCompletion(
        listOf("identity_op", "flat_map_identity", "map_identity"), """
        #[allow(clippy::ident/*caret*/)]
        fn foo() {}
    """)

    fun `test complete all in clippy`() = checkContainsCompletion(
        listOf("all"), """
        #[allow(clippy::al/*caret*/)]
        fn foo() {}
    """)

    fun `test warn`() = doSingleAttributeCompletion("""
        #![warn(unused_var/*caret*/)]
    """, """
        #![warn(unused_variables/*caret*/)]
    """)

    fun `test deny`() = doSingleAttributeCompletion("""
        #![deny(unused_var/*caret*/)]
    """, """
        #![deny(unused_variables/*caret*/)]
    """)

    fun `test forbid`() = doSingleAttributeCompletion("""
        #![forbid(unused_var/*caret*/)]
    """, """
        #![forbid(unused_variables/*caret*/)]
    """)

    fun `test do not complete path with leading double colon`() = checkNoCompletion("""
        #![allow(::/*caret*/)]
    """)
}
