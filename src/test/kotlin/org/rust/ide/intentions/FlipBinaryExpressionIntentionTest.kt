/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class FlipBinaryExpressionIntentionTest : RsIntentionTestBase(FlipBinaryExpressionIntention()) {

    fun `test +`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            x /*caret*/+ y;
        }
    """, """
        fn test(x: i32, y: i32) {
            y /*caret*/+ x;
        }
    """)

    fun `test *`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            x * y/*caret*/;
        }
    """, """
        fn test(x: i32, y: i32) {
            y * x/*caret*/;
        }
    """)

    fun `test ||`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            /*caret*/x || y;
        }
    """, """
        fn test(x: i32, y: i32) {
            /*caret*/y || x;
        }
    """)

    fun `test &&`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            /*caret*/x && y;
        }
    """, """
        fn test(x: i32, y: i32) {
            /*caret*/y && x;
        }
    """)

    fun `test ==`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            /*caret*/x == y;
        }
    """, """
        fn test(x: i32, y: i32) {
            /*caret*/y == x;
        }
    """)

    fun `test !=`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            /*caret*/x != y;
        }
    """, """
        fn test(x: i32, y: i32) {
            /*caret*/y != x;
        }
    """)

    fun `test gt`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            /*caret*/x > y;
        }
    """, """
        fn test(x: i32, y: i32) {
            /*caret*/y < x;
        }
    """)

    fun `test gt=`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            /*caret*/x >= y;
        }
    """, """
        fn test(x: i32, y: i32) {
            /*caret*/y <= x;
        }
    """)

    fun `test lt`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            /*caret*/x < y;
        }
    """, """
        fn test(x: i32, y: i32) {
            /*caret*/y > x;
        }
    """)

    fun `test lt=`() = doAvailableTest("""
        fn test(x: i32, y: i32) {
            /*caret*/x <= y;
        }
    """, """
        fn test(x: i32, y: i32) {
            /*caret*/y >= x;
        }
    """)

    fun `test -`() = doUnavailableTest(
        """
        fn test(x: i32, y: i32) {
            /*caret*/x - y;
        }
        """
    )

}
