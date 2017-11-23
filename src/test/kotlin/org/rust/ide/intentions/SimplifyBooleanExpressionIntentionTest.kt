/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

/**
 * @author Moklev Vyacheslav
 */
class SimplifyBooleanExpressionIntentionTest : RsIntentionTestBase(SimplifyBooleanExpressionIntention()) {
    fun `test or`() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/|| false;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test and`() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/&& false;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun `test xor`() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/^ false;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test not`() = doAvailableTest("""
        fn main() {
            let a = !/*caret*/true;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun `test parens`() = doAvailableTest("""
        fn main() {
            let a = (/*caret*/true);
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test short circuit or 1`() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/|| b;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test short circuit or 2`() = doAvailableTest("""
        fn main() {
            let a = false /*caret*/|| a;
        }
    """, """
        fn main() {
            let a = a;
        }
    """)

    fun `test short circuit and 1`() = doAvailableTest("""
        fn main() {
            let a = false /*caret*/&& b;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun `test short circuit and 2`() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/&& a;
        }
    """, """
        fn main() {
            let a = a;
        }
    """)

    fun `test non equivalent 1`() = doAvailableTest("""
        fn main() {
            let a = a ||/*caret*/ true || true;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test non equivalent 2`() = doAvailableTest("""
        fn main() {
            let a = a ||/*caret*/ false;
        }
    """, """
        fn main() {
            let a = a;
        }
    """)

    fun `test non equivalent 3`() = doAvailableTest("""
        fn main() {
            let a = a &&/*caret*/ false;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun `test non equivalent 4`() = doAvailableTest("""
        fn main() {
            let a = a &&/*caret*/ true;
        }
    """, """
        fn main() {
            let a = a;
        }
    """)

    fun `test complex non equivalent 1`() = doAvailableTest("""
        fn main() {
            let a = f() && (g() &&/*caret*/ false);
        }
    """, """
        fn main() {
            let a = f() && (false);
        }
    """)

    fun `test complex non equivalent 2`() = doAvailableTest("""
        fn main() {
            let a = 1 > 2 &&/*caret*/ 2 > 3 && 3 > 4 || true;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test complex non equivalent 3`() = doAvailableTest("""
        fn main() {
            let a = 1 > 2 &&/*caret*/ 2 > 3 && 3 > 4 || false;
        }
    """, """
        fn main() {
            let a = 1 > 2 && 2 > 3 && 3 > 4;
        }
    """)

    fun `test not available 3`() = doUnavailableTest("""
        fn main() {
            let a = a /*caret*/&& b;
        }
    """)

    fun `test not available 4`() = doUnavailableTest("""
        fn main() {
            let a = true /*caret*/^ a;
        }
    """)

    fun `test not available 5`() = doUnavailableTest("""
        fn main() {
            let a =  !/*caret*/a;
        }
    """)

    fun `test not available 6`() = doUnavailableTest("""
        fn main() {
            let a = /*caret*/true;
        }
    """)

    fun `test complex 1`() = doAvailableTest("""
        fn main() {
            let a = !(false ^ false) /*caret*/|| b;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test complex 2`() = doAvailableTest("""
        fn main() {
            let a = !(false /*caret*/^ false) || b;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test complex 3`() = doAvailableTest("""
        fn main() {
            let a = ((((((((((true)))) || b && /*caret*/c && d))))));
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test complex 4`() = doAvailableTest("""
        fn main() {
            let a = true || x >= y + z || foo(1, 2, r) == 42 || flag || (flag2 && !flag3/*caret*/);
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test find last`() = doAvailableTest("""
        fn main() {
            let a = true || x > 0 ||/*caret*/ x < 0 || y > 2 || y < 2 || flag;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun `test incomplete code`() = doUnavailableTest("""
        fn main() {
            xs.iter()
                .map(|/*caret*/)
        }
    """)
}
