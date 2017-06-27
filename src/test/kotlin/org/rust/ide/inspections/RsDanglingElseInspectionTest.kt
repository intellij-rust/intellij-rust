/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for Dangling Else inspection.
 */
class RsDanglingElseInspectionTest : RsInspectionsTestBase(RsDanglingElseInspection()) {

    fun testSimple() = checkByText("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else
            if</warning> true {
            }
        }
    """)

    fun testElseOnSeparateLine() = checkByText("""
        fn main() {
            if true {
            }
            <warning descr="Suspicious `else if` formatting">else
            if</warning> true {
            }
        }
    """)

    fun testMultipleNewLines() = checkByText("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else


            if</warning> true {
            }
        }
    """)

    fun testComments() = checkByText("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else

            // comments
            /* inside */

            if</warning> true {
            }
        }
    """)

    fun testNotAppliedWhenNoIf() = checkByText("""
        fn main() {
            if true {
            } else {
            }
        }
    """)

    fun testNotAppliedWhenNotDangling() = checkByText("""
        fn main() {
            if true {
            } else if false {
            }
        }
    """)

    fun testFixRemoveElse() = checkFixByText("Remove `else`", """
        fn main() {
            if true {
            }    <warning descr="Suspicious `else if` formatting">els<caret>e
            if</warning> false {
            }
        }
    """, """
        fn main() {
            if true {
            }
            if false {
            }
        }
    """)

    fun testFixJoin() = checkFixByText("Join `else if`", """
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else

            <caret>if</warning> false {
            }
        }
    """, """
        fn main() {
            if true {
            } else if false {
            }
        }
    """)

}
