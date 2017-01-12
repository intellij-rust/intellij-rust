package org.rust.ide.intentions

class DemorgansLawIntentionTest : RsIntentionTestBase(DemorgansLawIntention()) {

    fun testOr() = doAvailableTest("""
        fn main() {
            if a /*caret*/|| b {}
        }
    """, """
        fn main() {
            if !(!a && !b) {}
        }
    """)

    fun testOrNot() = doAvailableTest("""
        fn main() {
            if !(a /*caret*/|| b) {}
        }
    """, """
        fn main() {
            if !a && !b {}
        }
    """)

    fun testNotOr() = doAvailableTest("""
        fn main() {
            if !a /*caret*/|| !b {}
        }
    """, """
        fn main() {
            if !(a && b) {}
        }
    """)

    fun testComplex1() = doAvailableTest("""
        fn main() {
            if (a && b && c) /*caret*/|| d {}
        }
    """, """
        fn main() {
            if !(!(a && b && c) && !d) {}
        }
    """)

    fun testReverseComplex1() = doAvailableTest("""
        fn main() {
            if !(!(a && b && c) /*caret*/&& !d) {}
        }
    """, """
        fn main() {
            if (a && b && c) || d {}
        }
    """)

    fun testComplex2() = doAvailableTest("""
        fn main() {
            if (20 >= 50) /*caret*/&& 40 != 20 {}
        }
    """, """
        fn main() {
            if !((20 < 50) || 40 == 20) {}
        }
    """)

    fun testReverseComplex2() = doAvailableTest("""
        fn main() {
            if !((20 < 50) /*caret*/|| 40 == 20) {}
        }
    """, """
        fn main() {
            if (20 >= 50) && 40 != 20 {}
        }
    """)

    fun testComplex3() = doAvailableTest("""
        fn main() {
            if !(2 + 2 == 2 /*caret*/&& (foo.bar() || !(78 < 90 || 90 > 78)) || (20 >= 50 && ((40 != 20)))) {}
        }
    """, """
        fn main() {
            if !(!(2 + 2 != 2 || !(foo.bar() || !(78 < 90 || 90 > 78))) || (!(20 < 50 || ((40 == 20))))) {}
        }
    """)

    fun testReverseComplex3() = doAvailableTest("""
        fn main() {
            if !(!(2 + 2 != 2 /*caret*/|| !(foo.bar() || !(78 < 90 || 90 > 78))) || (!(20 < 50 || !((40 != 20))))) {}
        }
    """, """
        fn main() {
            if !(2 + 2 == 2 && (foo.bar() || !(78 < 90 || 90 > 78)) || (20 >= 50 && ((40 != 20)))) {}
        }
    """)

    fun testComplex4() = doAvailableTest("""
        fn main() {
            if 20 >= 50 /*caret*/&& 40 != 20 {}
        }
    """, """
        fn main() {
            if !(20 < 50 || 40 == 20) {}
        }
    """)

    fun testReverseComplex4() = doAvailableTest("""
        fn main() {
            if !(20 < 50 /*caret*/|| 40 == 20) {}
        }
    """, """
        fn main() {
            if 20 >= 50 && 40 != 20 {}
        }
    """)
}
