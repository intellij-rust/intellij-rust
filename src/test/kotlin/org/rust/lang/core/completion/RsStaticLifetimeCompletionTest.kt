/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsStaticLifetimeCompletionTest : RsCompletionTestBase() {
    fun `test complete static lifetime`() = doSingleCompletion("""
        fn foo(x: &'s/*caret*/ str) {}
    """, """
        fn foo(x: &'static/*caret*/ str) {}
    """)

    fun `test prefer local lifetime`() = doFirstCompletion("""
        fn foo<'stat>(x: &'s/*caret*/ str) {}
    """, """
        fn foo<'stat>(x: &'stat/*caret*/ str) {}
    """)
}
