/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ReplaceLineCommentWithBlockCommentIntentionTest : RsIntentionTestBase(ReplaceLineCommentWithBlockCommentIntention::class) {

    fun `test convert single line comment to block`() = doAvailableTest("""
        // /*caret*/Hello, World!
    """, """
        /* /*caret*/Hello, World! */
    """)

    fun `test convert multiple line comments to block`() = doAvailableTest("""
        // First
		// /*caret*/Second
		// Third
    """, """
        /*
        First
        Second
        Third
        *//*caret*/
        
    """)

    fun `test convert multiple line comments with spaces to block`() = doAvailableTest("""
        // First
        
		// /*caret*/Second
        
        // Third
    """, """
        /*
        First
        Second
        Third
        *//*caret*/
        
    """)

    fun `test convert multiple line comments with indent to block`() = doAvailableTest("""
        fn foo() {
            // First
            // Second/*caret*/
            let x = 1;
        }
    """, """
        fn foo() {
            /*
            First
            Second
            */
            /*caret*/let x = 1;
        }
    """)

    // TODO: This does not work because EOL comments are children of `RsFunction` and `prevSibling` is `null`
    fun `test convert multiple line comments with indent members`() = expect<AssertionError> {
        doAvailableTest("""
        trait Foo {
            // First
            // Second/*caret*/
            fn foo() {}
        }
    """, """
        trait Foo {
            /*
            First
            Second
            */
            /*caret*/fn foo() {}
        }
    """)
    }
}
