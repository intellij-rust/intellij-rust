/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class RemoveParenthesesFromExprIntentionTest : RsIntentionTestBase(RemoveParenthesesFromExprIntention()) {
    fun `test remove parentheses from expr`() = doAvailableTest("""
        fn test() {
            let a = (4 + 3/*caret*/);
        }
    """, """
        fn test() {
            let a = 4 + 3/*caret*/;
        }
    """)
}
