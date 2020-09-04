/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ChopArgumentListIntentionTest : RsIntentionTestBase(ChopArgumentListIntention::class) {
    fun `test one parameter`() = doUnavailableTest("""
        fn foo(p1: i32) {}
        fn main() {
            foo(/*caret*/1);
        }
    """)

    fun `test two parameter`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32) {}
        fn main() {
            foo(/*caret*/1, 2);
        }
    """, """
        fn foo(p1: i32, p2: i32) {}
        fn main() {
            foo(
                1,
                2
            );
        }
    """)

    fun `test has all line breaks`() = doUnavailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(
                /*caret*/1,
                2,
                3
            );
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
            foo(
                1,
                2,
                3
            );
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
            foo(
                1,
                2,
                3
            );
        }
    """)

    fun `test has comment`() = doUnavailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(
                /*caret*/1, /* comment */
                2,
                3
            );
        }
    """)

    fun `test has comment 2`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(
                /*caret*/1, /*
                    comment
                */2,
                3
            );
        }
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(
                1, /*
                    comment
                */
                2,
                3
            );
        }
    """)

    fun `test has single line comment`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(/*caret*/1, // comment 1
                2, 3 // comment 3
            );
        }
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(
                1, // comment 1
                2,
                3 // comment 3
            );
        }
    """)

    fun `test trailing comma`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(/*caret*/1, 2, 3,);
        }
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(
                1,
                2,
                3,
            );
        }
    """)

    fun `test trailing comma with comments`() = doAvailableTest("""
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(/*caret*/1 /* comment 1 */, 2, 3 /* comment 2 */, );
        }
    """, """
        fn foo(p1: i32, p2: i32, p3: i32) {}
        fn main() {
            foo(
                1 /* comment 1 */,
                2,
                3 /* comment 2 */,
            );
        }
    """)
}
