/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ChopParameterListIntentionTest : RsIntentionTestBase(ChopParameterListIntention::class) {
    fun `test one parameter`() = doUnavailableTest("""
        fn foo(/*caret*/p1: i32) {}
    """)

    fun `test two parameter`() = doAvailableTest("""
        fn foo(/*caret*/p1: i32, p2: i32) {}
    """, """
        fn foo(
            p1: i32,
            p2: i32
        ) {}
    """)

    fun `test has all line breaks`() = doUnavailableTest("""
        fn foo(
            /*caret*/p1: i32,
            p2: i32,
            p3: i32
        ) {}
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        fn foo(p1: i32, /*caret*/p2: i32,
               p3: i32
        ) {}
    """, """
        fn foo(
            p1: i32,
            p2: i32,
            p3: i32
        ) {}
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        fn foo(
            p1: i32, p2: i32, p3: i32/*caret*/
        ) {}
    """, """
        fn foo(
            p1: i32,
            p2: i32,
            p3: i32
        ) {}
    """)

    fun `test has comment`() = doUnavailableTest("""
        fn foo(
            /*caret*/p1: i32, /* comment */
            p2: i32,
            p3: i32
        ) {}
    """)

    fun `test has comment 2`() = doAvailableTest("""
        fn foo(
            /*caret*/p1: i32, /*
                comment
            */p2: i32,
            p3: i32
        ) {}
    """, """
        fn foo(
            p1: i32, /*
                comment
            */
            p2: i32,
            p3: i32
        ) {}
    """)

    fun `test has single line comment`() = doAvailableTest("""
        fn foo(/*caret*/p1: i32, // comment p1
               p2: i32, p3: i32 // comment p3
        ) {}
    """, """
        fn foo(
            p1: i32, // comment p1
            p2: i32,
            p3: i32 // comment p3
        ) {}
    """)

    fun `test trailing comma`() = doAvailableTest("""
        fn foo(/*caret*/p1: i32, p2: i32, p3: i32,) {}
    """, """
        fn foo(
            p1: i32,
            p2: i32,
            p3: i32,
        ) {}
    """)

    fun `test trailing comma with comments`() = doAvailableTest("""
        fn foo(/*caret*/p1: i32 /* comment 1 */, p2: i32, p3: i32 /* comment 2 */,) {}
    """, """
        fn foo(
            p1: i32 /* comment 1 */,
            p2: i32,
            p3: i32 /* comment 2 */,
        ) {}
    """)
}
