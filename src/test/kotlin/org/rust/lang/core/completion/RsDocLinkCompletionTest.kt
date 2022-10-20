/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsDocLinkCompletionTest : RsCompletionTestBase() {

    fun `test unqualified path`() = checkContainsCompletion(
        listOf("bar", "crate", "self"),
    """
        /// [link]: /*caret*/
        fn foo() {}
        fn bar() {}
    """)

    fun `test qualified path`() = checkContainsCompletion("bar", """
        /// [link]: inner::/*caret*/
        fn foo() {}
        mod inner {
            pub fn bar() {}
        }
    """)
}
