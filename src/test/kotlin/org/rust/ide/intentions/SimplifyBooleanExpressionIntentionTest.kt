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

    fun testShortCircuitOr1() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/|| b;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testShortCircuitOr2() = doAvailableTest("""
        fn main() {
            let a = false /*caret*/|| a;
        }
    """, """
        fn main() {
            let a = a;
        }
    """)

    fun testShortCircuitAnd1() = doAvailableTest("""
        fn main() {
            let a = false /*caret*/&& b;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun testShortCircuitAnd2() = doAvailableTest("""
        fn main() {
            let a = true /*caret*/&& a;
        }
    """, """
        fn main() {
            let a = a;
        }
    """)

    fun testNonEquivalent1() = doAvailableTest("""
        fn main() {
            let a = a ||/*caret*/ true || true;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testNonEquivalent2() = doAvailableTest("""
        fn main() {
            let a = a ||/*caret*/ false;
        }
    """, """
        fn main() {
            let a = a;
        }
    """)

    fun testNonEquivalent3() = doAvailableTest("""
        fn main() {
            let a = a &&/*caret*/ false;
        }
    """, """
        fn main() {
            let a = false;
        }
    """)

    fun testNonEquivalent4() = doAvailableTest("""
        fn main() {
            let a = a &&/*caret*/ true;
        }
    """, """
        fn main() {
            let a = a;
        }
    """)

    fun testComplexNonEquivalent1() = doAvailableTest("""
        fn main() {
            let a = f() && (g() &&/*caret*/ false);
        }
    """, """
        fn main() {
            let a = f() && (false);
        }
    """)

    fun testComplexNonEquivalent2() = doAvailableTest("""
        fn main() {
            let a = 1 > 2 &&/*caret*/ 2 > 3 && 3 > 4 || true;
        }
    """, """
        fn main() {
            let a = true;
        }
    """)

    fun testComplexNonEquivalent3() = doAvailableTest("""
        fn main() {
            let a = 1 > 2 &&/*caret*/ 2 > 3 && 3 > 4 || false;
        }
    """, """
        fn main() {
            let a = 1 > 2 && 2 > 3 && 3 > 4;
        }
    """)

    fun testNotAvailable3() = doUnavailableTest("""
        fn main() {
            let a = a /*caret*/&& b;
        }
    """)

    fun testNotAvailable4() = doUnavailableTest("""
        fn main() {
            let a = true /*caret*/^ a;
        }
    """)

    fun testNotAvailable5() = doUnavailableTest("""
        fn main() {
            let a =  !/*caret*/a;
        }
    """)

    fun testNotAvailable6() = doUnavailableTest("""
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

    fun `test incomplete code`() = doUnavailableTest("""
        fn main() {
            xs.iter()
                .map(|/*caret*/)
        }
    """)
}
