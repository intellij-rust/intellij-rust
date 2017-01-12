package org.rust.ide.intentions

/**
 * @author Moklev Vyacheslav
 */
class SimplifyBooleanExpressionIntentionTest : RsIntentionTestBase(SimplifyBooleanExpressionIntention()) {
    fun testOr() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/|| false;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testAnd() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/&& false;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun testXor() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/^ false;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testNot() = doAvailableTest("""
        fn main() {
            let a = !/*caret*/true;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun testParens() = doAvailableTest("""
        fn main() {
            let a = (/*caret*/true);
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testShortCircuitOr() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/|| b;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testShortCircuitAnd() = doAvailableTest("""
        fn main() {
            let a = false /*caret*/&& b;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun testNotAvailable1() = doUnavailableTest("""
        fn main() {
            let a = true /*caret*/&& a;
        }
    """)

    fun testNotAvailable2() = doUnavailableTest("""
        fn main() {
            let a = false /*caret*/|| a;
        }
    """)

    fun testNotAvailable3() = doUnavailableTest("""
        fn main() {
            let a = a /*caret*/&& b;
        }
    """)

    fun testNotAvailable4() = doUnavailableTest("""
        fn main() {
            let a = a ||/*caret*/ true || true;
        }
    """)

    fun testNotAvailable5() = doUnavailableTest("""
        fn main() {
            let a = true /*caret*/^ a;
        }
    """)

    fun testNotAvailable6() = doUnavailableTest("""
        fn main() {
            let a =  !/*caret*/a;
        }
    """)

    fun testNotAvailable7() = doUnavailableTest("""
        fn main() {
            let a = /*caret*/true;
        }
    """)

    fun testComplex1() = doAvailableTest("""
        fn main() {
            let a = !(false ^ false) /*caret*/|| b;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testComplex2() = doAvailableTest("""
        fn main() {
            let a = !(false /*caret*/^ false) || b;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testComplex3() = doAvailableTest("""
        fn main() {
            let a = ((((((((((true)))) || b && /*caret*/c && d))))));
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testComplex4() = doAvailableTest("""
        fn main() {
            let a = true || x >= y + z || foo(1, 2, r) == 42 || flag || (flag2 && !flag3/*caret*/);
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testFindLast() = doAvailableTest("""
        fn main() {
            let a = true || x > 0 ||/*caret*/ x < 0 || y > 2 || y < 2 || flag;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)
}
