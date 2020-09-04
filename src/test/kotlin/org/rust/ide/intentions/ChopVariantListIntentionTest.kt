/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ChopVariantListIntentionTest : RsIntentionTestBase(ChopVariantListIntention::class) {
    fun `test one parameter`() = doUnavailableTest("""
        enum E { /*caret*/A }
    """)

    fun `test two parameter`() = doAvailableTest("""
        enum E { /*caret*/A(i32, i32), B }
    """, """
        enum E {
            A(i32, i32),
            B
        }
    """)

    fun `test has all line breaks`() = doUnavailableTest("""
        enum E {
            /*caret*/A,
            B,
            C
        }
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        enum E { A, /*caret*/B,
                   C
        }
    """, """
        enum E {
            A,
            B,
            C
        }
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        enum E { 
            A, B, C/*caret*/
        }
    """, """
        enum E {
            A,
            B,
            C
        }
    """)

    fun `test has comment`() = doUnavailableTest("""
        enum E { 
            /*caret*/A, /* comment */ 
            B,
            C
        }
    """)

    fun `test has comment 2`() = doAvailableTest("""
        enum E { 
            /*caret*/A, /*
                comment
            */B,
            C
        }
    """, """
        enum E {
            A, /*
                comment
            */
            B,
            C
        }
    """)

    fun `test has single line comment`() = doAvailableTest("""
        enum E {
            /*caret*/A, // comment A
            B, C // comment C
        }
    """, """
        enum E {
            A,
            // comment A
            B,
            C // comment C
        }
    """)

    fun `test trailing comma`() = doAvailableTest("""
        enum E { /*caret*/A, B, C, }
    """, """
        enum E {
            A,
            B,
            C,
        }
    """)

    fun `test trailing comma with comments`() = doAvailableTest("""
        enum E { /*caret*/A /* comment 1 */, B, C /* comment 2 */, }
    """, """
        enum E {
            A /* comment 1 */,
            B,
            C /* comment 2 */,
        }
    """)
}
