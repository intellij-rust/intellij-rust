/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class JoinArgumentListIntentionTest : RsIntentionTestBase(JoinArgumentListIntention::class) {
    fun `test one parameter`() = doAvailableTest("""
        fn foo(p1: i32) {}
        fn main() {
            foo(
                /*caret*/1
            );
        }
    """, """
        fn foo(p1: i32) {}
        fn main() {
            foo(1);
        }
    """)

    fun `test two parameter`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32) {}
        fn main() {
            foo(
                /*caret*/1,
                2
            );
        }
    """, """
        fn foo(p1: i32, p2: i32) {}
        fn main() {
            foo(1, 2);
        }
    """)

    fun `test no line breaks`() = doUnavailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(/*caret*/1, 2, 3);
        }
    """)

    fun `test has some line breaks`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(1, /*caret*/2,
                3
            );
        }
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(1, 2, 3);
        }
    """)

    fun `test has some line breaks 2`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(
                1, 2, 3/*caret*/
            );
        }
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(1, 2, 3);
        }
    """)

    fun `test has comment`() = doUnavailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(/*caret*/1, /* comment */ 2, 3);
        }
    """)

    fun `test has comment 2`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(/*caret*/1, /*
                comment
                */ 2,
                3);
        }
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(1, /*
                comment
                */ 2, 3);
        }
    """)

    fun `test has end-of-line comments`() = doUnavailableTest("""
        fn foo(p1: i32, p2: i32) {}
        fn main() {
            foo(
                /*caret*/1, // comment
                2
            );
        }
    """)
}
