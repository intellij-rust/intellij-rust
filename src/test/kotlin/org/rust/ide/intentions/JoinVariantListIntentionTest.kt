/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class JoinVariantListIntentionTest : RsIntentionTestBase(JoinVariantListIntention()) {
    fun `test one parameter`() = doAvailableTest("""
        enum E {
            /*caret*/A
        }
    """, """enum E { A }""")

    fun `test two parameter`() = doAvailableTest("""
        enum E {
            /*caret*/A(i32, i32),
            B
        }
    """, """
        enum E { A(i32, i32), B }
    """)

    fun `test no line breaks`() = doUnavailableTest("""
        enum E { /*caret*/A, B, C }
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        enum E { A, /*caret*/B,
                   C }
    """, """
        enum E { A, B, C }
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        enum E {
            A, B, C/*caret*/
        }
    """, """
        enum E { A, B, C }
    """)

    fun `test has comment`() = doUnavailableTest("""
        enum E { /*caret*/A, /* comment */ B, C }
    """)

    fun `test has comment 2`() = doAvailableTest("""
        enum E { /*caret*/A, /*
                   comment
                   */ B,
                   C
        }
    """, """
        enum E { A, /*
                   comment
                   */ B, C }
    """)

    fun `test has end-of-line comments`() = doUnavailableTest("""
        enum E {
            /*caret*/A(i32, i32), // comment
            B
        }
    """)
}
