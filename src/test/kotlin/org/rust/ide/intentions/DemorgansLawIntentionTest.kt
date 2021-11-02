/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class DemorgansLawIntentionTest : RsIntentionTestBase(DemorgansLawIntention::class) {

    fun `test or`() = doAvailableSymmetricTest("""
        fn main() {
            if a /*caret*/|| b {}
        }
    """, """
        fn main() {
            if !(!a /*caret*/&& !b) {}
        }
    """)

    fun `test or not`() = doAvailableSymmetricTest("""
        fn main() {
            if !(a /*caret*/|| b) {}
        }
    """, """
        fn main() {
            if !a /*caret*/&& !b {}
        }
    """)

    fun `test not or`() = doAvailableSymmetricTest("""
        fn main() {
            if !a /*caret*/|| !b {}
        }
    """, """
        fn main() {
            if !(a /*caret*/&& b) {}
        }
    """)

    fun `test complex 1`() = doAvailableSymmetricTest("""
        fn main() {
            if (a && b && c) /*caret*/|| d {}
        }
    """, """
        fn main() {
            if !(!(a && b && c) /*caret*/&& !d) {}
        }
    """)

    fun `test complex 2`() = doAvailableSymmetricTest("""
        fn main() {
            if (20 >= 50) /*caret*/&& 40 != 20 {}
        }
    """, """
        fn main() {
            if !((20 < 50) /*caret*/|| 40 == 20) {}
        }
    """)

    fun `test complex 3`() = doAvailableSymmetricTest("""
        fn main() {
            if !(2 + 2 == 2 /*caret*/&& (foo.bar() || !(78 < 90 || 90 > 78)) || (20 >= 50 && ((40 != 20)))) {}
        }
    """, """
        fn main() {
            if !(!(2 + 2 != 2 /*caret*/|| !(foo.bar() || !(78 < 90 || 90 > 78))) || (!(20 < 50 || ((40 == 20))))) {}
        }
    """)

    fun `test complex 4`() = doAvailableSymmetricTest("""
        fn main() {
            if 20 >= 50 /*caret*/&& 40 != 20 {}
        }
    """, """
        fn main() {
            if !(20 < 50 /*caret*/|| 40 == 20) {}
        }
    """)

    fun `test constant`() = doAvailableSymmetricTest("""
        fn main() {
            let _ = b /*caret*/|| true;
        }
    """, """
        fn main() {
            let _ = !(!b /*caret*/&& !true);
        }
    """)
}
