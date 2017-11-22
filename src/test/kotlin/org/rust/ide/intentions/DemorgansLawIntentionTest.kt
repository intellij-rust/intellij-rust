/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class DemorgansLawIntentionTest : RsIntentionTestBase(DemorgansLawIntention()) {

    fun `test or`() = doAvailableTest("""
        fn main() {
            if a /*caret*/|| b {}
        }
    """, """
        fn main() {
            if !(!a && !b) {}
        }
    """)

    fun `test or not`() = doAvailableTest("""
        fn main() {
            if !(a /*caret*/|| b) {}
        }
    """, """
        fn main() {
            if !a && !b {}
        }
    """)

    fun `test not or`() = doAvailableTest("""
        fn main() {
            if !a /*caret*/|| !b {}
        }
    """, """
        fn main() {
            if !(a && b) {}
        }
    """)

    fun `test complex 1`() = doAvailableTest("""
        fn main() {
            if (a && b && c) /*caret*/|| d {}
        }
    """, """
        fn main() {
            if !(!(a && b && c) && !d) {}
        }
    """)

    fun `test reverse complex 1`() = doAvailableTest("""
        fn main() {
            if !(!(a && b && c) /*caret*/&& !d) {}
        }
    """, """
        fn main() {
            if (a && b && c) || d {}
        }
    """)

    fun `test complex 2`() = doAvailableTest("""
        fn main() {
            if (20 >= 50) /*caret*/&& 40 != 20 {}
        }
    """, """
        fn main() {
            if !((20 < 50) || 40 == 20) {}
        }
    """)

    fun `test reverse complex 2`() = doAvailableTest("""
        fn main() {
            if !((20 < 50) /*caret*/|| 40 == 20) {}
        }
    """, """
        fn main() {
            if (20 >= 50) && 40 != 20 {}
        }
    """)

    fun `test complex 3`() = doAvailableTest("""
        fn main() {
            if !(2 + 2 == 2 /*caret*/&& (foo.bar() || !(78 < 90 || 90 > 78)) || (20 >= 50 && ((40 != 20)))) {}
        }
    """, """
        fn main() {
            if !(!(2 + 2 != 2 || !(foo.bar() || !(78 < 90 || 90 > 78))) || (!(20 < 50 || ((40 == 20))))) {}
        }
    """)

    fun `test reverse complex 3`() = doAvailableTest("""
        fn main() {
            if !(!(2 + 2 != 2 /*caret*/|| !(foo.bar() || !(78 < 90 || 90 > 78))) || (!(20 < 50 || !((40 != 20))))) {}
        }
    """, """
        fn main() {
            if !(2 + 2 == 2 && (foo.bar() || !(78 < 90 || 90 > 78)) || (20 >= 50 && ((40 != 20)))) {}
        }
    """)

    fun `test complex 4`() = doAvailableTest("""
        fn main() {
            if 20 >= 50 /*caret*/&& 40 != 20 {}
        }
    """, """
        fn main() {
            if !(20 < 50 || 40 == 20) {}
        }
    """)

    fun `test reverse complex 4`() = doAvailableTest("""
        fn main() {
            if !(20 < 50 /*caret*/|| 40 == 20) {}
        }
    """, """
        fn main() {
            if 20 >= 50 && 40 != 20 {}
        }
    """)

    fun `test constant`() = doAvailableTest("""
        fn main() {
            let _ = b /*caret*/|| true;
        }
    """, """
        fn main() {
            let _ = !(!b && !true);
        }
    """)
}
