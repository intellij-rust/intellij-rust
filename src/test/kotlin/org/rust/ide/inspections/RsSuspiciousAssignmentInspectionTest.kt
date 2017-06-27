/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for Suspicious Assignment inspection.
 */
class RsSuspiciousAssignmentInspectionTest : RsInspectionsTestBase(RsSuspiciousAssignmentInspection()) {

    fun testMinus() = checkByText("""
        fn main() {
            let mut a = 12;
            a<warning descr="Suspicious assignment. Did you mean `a -= 1` or `a = -1`?"> =- </warning>1;
        }
    """)

    fun testDeref() = checkByText("""
        fn main() {
            let mut a = &32;
            let b;
            b<warning descr="Suspicious assignment. Did you mean `b *= a` or `b = *a`?"> =* </warning>a;
        }
    """)

    fun testNot() = checkByText("""
        fn main() {
            let mut a = true;
            a<warning descr="Suspicious assignment. Did you mean `a != true` or `a = !true`?"> =! </warning>true;
        }
    """)

    fun testExpression() = checkByText("""
        fn main() {
            let mut a = 10;
            let b = 47;
            a<warning descr="Suspicious assignment. Did you mean `a -= b - 19` or `a = -b - 19`?"> =- </warning>b - 19;
        }
    """)

    fun testNoLeftSpace() = checkByText("""
        fn main() {
            let mut a = 72;
            a<warning descr="Suspicious assignment. Did you mean `a -= 10` or `a = -10`?">=- </warning>10;
        }
    """)

    fun testCompactLeftPart() = checkByText("""
        fn main() {
            let mut foo_bar_baz = 7;
            foo_bar_baz<warning descr="Suspicious assignment. Did you mean `.. -= 1` or `.. = -1`?"> =- </warning>1;
        }
    """)

    fun testCompactRightPart() = checkByText("""
        fn main() {
            let mut a = 7;
            a<warning descr="Suspicious assignment. Did you mean `a -= ..` or `a = (-..)`?"> =- </warning>10000 * a^2 + 714;
        }
    """)

    fun testNotApplied() = checkByText("""
        fn main() {
            let mut a = 7;
            a =-3;
        }
    """)

    fun testFix1() = checkFixByText("Change to `a -= 1`", """
        fn main() {
            let mut a = 10;
            a<warning descr="Suspicious assignment. Did you mean `a -= 1` or `a = -1`?"> =<caret>- </warning>1;
        }
    """, """
        fn main() {
            let mut a = 10;
            a -= 1;
        }
    """)

    fun testFix2() = checkFixByText("Change to `a = -12 + 3`", """
        fn main() {
            let mut a = 10;
            a<warning descr="Suspicious assignment. Did you mean `a -= 12 + 3` or `a = -12 + 3`?"> =<caret>- </warning>12 + 3;
        }
    """, """
        fn main() {
            let mut a = 10;
            a = -12 + 3;
        }
    """)
}
