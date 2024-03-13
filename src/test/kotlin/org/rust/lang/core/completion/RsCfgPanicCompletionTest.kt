/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsCfgPanicCompletionTest : RsCompletionTestBase() {
    fun `test simple in literal`() = checkCompletion("unwind", """
        #[cfg(panic = "/*caret*/")]
        fn foo() {}
        """, """
        #[cfg(panic = "unwind")]
        fn foo() {}
    """)

    fun `test simple without literal`() = checkCompletion("unwind", """
        #[cfg(panic = /*caret*/)]
        fn foo() {}
        """, """
        #[cfg(panic = "unwind")]
        fn foo() {}
    """)

    fun `test complex in literal`() = doSingleCompletion("""
        #[cfg(any(panic = "abort", panic = "u/*caret*/"))]
        fn foo() {}
        """, """
        #[cfg(any(panic = "abort", panic = "unwind/*caret*/"))]
        fn foo() {}
    """)

    fun `test complex without literal`() = doSingleCompletion("""
        #[cfg(any(panic = "abort", panic = u/*caret*/))]
        fn foo() {}
        """, """
        #[cfg(any(panic = "abort", panic = "unwind/*caret*/"))]
        fn foo() {}
    """)
}
