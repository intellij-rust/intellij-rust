/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class JoinParameterListIntentionTest : RsIntentionTestBase(JoinParameterListIntention::class) {
    fun `test one parameter`() = doAvailableTest("""
        fn foo(
            /*caret*/p1: i32
        ) {}
    """, """fn foo(p1: i32) {}""")

    fun `test two parameter`() = doAvailableTest("""
        fn foo(
            /*caret*/p1: i32,
            p2: i32
        ) {}
    """, """
        fn foo(p1: i32, p2: i32) {}
    """)

    fun `test no line breaks`() = doUnavailableTest("""
        fn foo(/*caret*/p1: i32, p2: i32, p3: i32) {}
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        fn foo(p1: i32, /*caret*/p2: i32,
               p3: i32
        ) {}
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        fn foo(
            p1: i32, p2: i32, p3: i32/*caret*/
        ) {}
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
    """)

    fun `test has comment`() = doUnavailableTest("""
        fn foo(/*caret*/p1: i32, /* comment */ p2: i32, p3: i32) {}
    """)

    fun `test has comment 2`() = doAvailableTest("""
        fn foo(/*caret*/p1: i32, /*
               comment
               */ p2: i32,
               p3: i32) {}
    """, """
        fn foo(p1: i32, /*
               comment
               */ p2: i32, p3: i32) {}
    """)

    fun `test has end-of-line comments`() = doUnavailableTest("""
        fn foo(
            /*caret*/p1: i32, // comment
            p2: i32
        ) {}
    """)
}
