/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ReplaceBlockCommentWithLineCommentIntentionTest : RsIntentionTestBase(ReplaceBlockCommentWithLineCommentIntention::class) {

    fun `test convert single block comment`() = doAvailableTest("""
        /* /*caret*/Hello, World! */
    """, """
        /*caret*/// Hello, World!
    """)

    fun `test convert multiline block comment`() = doAvailableTest("""
        /*
        First
        /*caret*/Second
        Third
        */
    """, """
        /*caret*/// First
        // Second
        // Third
    """)

    fun `test convert multiline block comment with spaces`() = doAvailableTest("""
        /*
        First
        
        
        Second/*caret*/ Third
        */
    """, """
        // First
        // 
        // 
        // Second Third
    """)

    fun `test convert multiline block comment with indent`() = doAvailableTest("""
        fn foo() {
            /*
            First
            Second/*caret*/
            */
            let x = 1;
        }
    """, """
        fn foo() {
            /*caret*/// First
            // Second
            let x = 1;
        }
    """)

    fun `test convert multiple line comments with indent members`() = doAvailableTest("""
        trait Foo {
            /*
            First
            Second/*caret*/
            */
            fn foo() {}
        }
    """, """
        trait Foo {
            /*caret*/// First
            // Second
            fn foo() {}
        }
    """)
}
