/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class AddDeriveIntentionTest : RsIntentionTestBase(AddDeriveIntention()) {

    fun `test add derive struct`() = doAvailableTest("""
        struct Te/*caret*/st {}
    """, """
        #[derive(/*caret*/)]
        struct Test {}
    """)

    fun `test add derive pub struct`() = doAvailableTest("""
        pub struct Te/*caret*/st {}
    """, """
        #[derive(/*caret*/)]
        pub struct Test {}
    """)

    // FIXME: there is something weird with enum re-formatting, for some reason it adds more indentation
    fun `test add derive enum`() = doAvailableTest("""
        enum Test /*caret*/{
            Something
        }
    """, """
        #[derive(/*caret*/)]
        enum Test {
            Something
        }
    """)

    fun `test add derive existing attr`() = doAvailableTest("""
        #[derive(Something)]
        struct Test/*caret*/ {}
    """, """
        #[derive(Something/*caret*/)]
        struct Test {}
    """)
}
