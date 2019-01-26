/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ExtractNestedBlockIntentionTest : RsIntentionTestBase(ExtractNestedBlockIntention()) {
    fun `test expr base case`() = doAvailableTest("""
        fn foo() {
            let mut x = 0;

            {
                x = bar();/*caret*/
            }
        }
    ""","""
        fn foo() {
            let mut x = 0;

            x = bar();
        }
    """)

    fun `test stmt base case`() = doAvailableTest("""
        fn foo() {
            let mut x = 0;

            {
                x = bar();/*caret*/
            }

            x = 0;
        }
    ""","""
        fn foo() {
            let mut x = 0;

            x = bar();

            x = 0;
        }
    """)

    fun `test double-nested block saved`() = doAvailableTest("""
        fn foo() {
            let mut x = 0;

            {/*caret*/
                x = bar();

                {
                    x = 0;
                }
            }
        }
    ""","""
        fn foo() {
            let mut x = 0;

            x = bar();

            {
                x = 0;
            }
        }
    """)

    fun `test outer block's rbrace is missing`() = doAvailableTest("""
        fn foo() {
            let mut x = 0;

            {
                x = bar();/*caret*/
            }
    ""","""
        fn foo() {
            let mut x = 0;

            x = bar();
            
    """)

    fun `test non-separate block`() = doUnavailableTest("""
        fn foo() {
            let mut x = 0;

            while x < 5 {/*caret*/
                x += 1;
            }
        }
    """)

    fun `test no outer block`() = doUnavailableTest("""
        {
            x = bar();/*caret*/
        }
    """)
}


