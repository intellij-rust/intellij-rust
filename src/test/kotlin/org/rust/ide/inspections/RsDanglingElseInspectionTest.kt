package org.rust.ide.inspections

/**
 * Tests for Dangling Else inspection.
 */
class RsDanglingElseInspectionTest : RsInspectionsTestBase() {

    fun testSimple() = checkByText<RsDanglingElseInspection>("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else
            if</warning> true {
            }
        }
    """)

    fun testElseOnSeparateLine() = checkByText<RsDanglingElseInspection>("""
        fn main() {
            if true {
            }
            <warning descr="Suspicious `else if` formatting">else
            if</warning> true {
            }
        }
    """)

    fun testMultipleNewLines() = checkByText<RsDanglingElseInspection>("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else


            if</warning> true {
            }
        }
    """)

    fun testComments() = checkByText<RsDanglingElseInspection>("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else

            // comments
            /* inside */

            if</warning> true {
            }
        }
    """)

    fun testNotAppliedWhenNoIf() = checkByText<RsDanglingElseInspection>("""
        fn main() {
            if true {
            } else {
            }
        }
    """)

    fun testNotAppliedWhenNotDangling() = checkByText<RsDanglingElseInspection>("""
        fn main() {
            if true {
            } else if false {
            }
        }
    """)

    fun testFixRemoveElse() = checkFixByText<RsDanglingElseInspection>("Remove `else`", """
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

    fun testFixJoin() = checkFixByText<RsDanglingElseInspection>("Join `else if`", """
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
