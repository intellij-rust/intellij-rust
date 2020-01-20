/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ReplaceBlockCommentWithLineCommentIntentionTest : RsIntentionTestBase(ReplaceBlockCommentWithLineCommentIntention()) {

    fun `test convert single block comment to line`() = doAvailableTest("""
        /* /*caret*/Hello, World! */
    """, """
        /*caret*///Hello, World!
    """)

    fun `test convert multiline block comment to line`() = doAvailableTestRaw("""
        /*
        First
        /*caret*/Second
        Third
        */
    """.trimIndent(), """
        /*caret*///First
        //Second
        //Third
    """.trimIndent())
}
