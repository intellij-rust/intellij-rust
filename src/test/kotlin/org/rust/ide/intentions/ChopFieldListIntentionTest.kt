/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ChopFieldListIntentionTest : RsIntentionTestBase(ChopFieldListIntention::class) {
    fun `test one parameter`() = doUnavailableTest("""
        struct S { /*caret*/x: i32 }
    """)

    fun `test two parameter`() = doAvailableTest("""
        struct S { /*caret*/x: i32, y: i32 }
    """, """
        struct S {
            x: i32,
            y: i32
        }
    """)

    fun `test has all line breaks`() = doUnavailableTest("""
        struct S {
            /*caret*/x: i32,
            y: i32,
            z: i32
        }
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        struct S { x: i32, /*caret*/y: i32,
                   z: i32
        }
    """, """
        struct S {
            x: i32,
            y: i32,
            z: i32
        }
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        struct S {
            x: i32, y: i32, z: i32/*caret*/
        }
    """, """
        struct S {
            x: i32,
            y: i32,
            z: i32
        }
    """)

    fun `test has comment`() = doUnavailableTest("""
        struct S {
            /*caret*/x: i32, /* comment */
            y: i32,
            z: i32
        }
    """)

    fun `test has comment 2`() = doAvailableTest("""
        struct S {
            /*caret*/x: i32, /*
                comment
            */y: i32,
            z: i32
        }
    """, """
        struct S {
            x: i32, /*
                comment
            */
            y: i32,
            z: i32
        }
    """)

    fun `test has single line comment`() = doAvailableTest("""
        struct S {
            /*caret*/x: i32, // comment x
            y: i32, z: i32 // comment z
        }
    """, """
        struct S {
            x: i32,
            // comment x
            y: i32,
            z: i32 // comment z
        }
    """)

    fun `test trailing comma`() = doAvailableTest("""
        struct S { /*caret*/x: i32, y: i32, z: i32, }
    """, """
        struct S {
            x: i32,
            y: i32,
            z: i32,
        }
    """)

    fun `test trailing comma with comments`() = doAvailableTest("""
        struct S { /*caret*/x: i32 /* comment 1 */, y: i32, z: i32 /* comment 2 */, }
    """, """
        struct S {
            x: i32 /* comment 1 */,
            y: i32,
            z: i32 /* comment 2 */,
        }
    """)
}
