/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class JoinFieldListIntentionTest : RsIntentionTestBase(JoinFieldListIntention()) {
    fun `test one parameter`() = doAvailableTest("""
        struct S {
            /*caret*/x: i32
        }
    """, """struct S { x: i32 }""")

    fun `test two parameter`() = doAvailableTest("""
        struct S {
            /*caret*/x: i32,
            y: i32
        }
    """, """
        struct S { x: i32, y: i32 }
    """)

    fun `test no line breaks`() = doUnavailableTest("""
        struct S { /*caret*/x: i32, y: i32, z: i32 }
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        struct S { x: i32, /*caret*/y: i32, 
                   z: i32 }
    """, """
        struct S { x: i32, y: i32, z: i32 }
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        struct S { 
            x: i32, y: i32, z: i32/*caret*/ 
        }
    """, """
        struct S { x: i32, y: i32, z: i32 }
    """)

    fun `test has comment`() = doUnavailableTest("""
        struct S { /*caret*/x: i32, /* comment */ y: i32, z: i32 }
    """)

    fun `test has comment 2`() = doAvailableTest("""
        struct S { /*caret*/x: i32, /*
                   comment
                   */ y: i32,
                   z: i32
        }
    """, """
        struct S { x: i32, /*
                   comment
                   */ y: i32, z: i32 }
    """)
}
