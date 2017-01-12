package org.rust.ide.inspections

/**
 * Tests for Dangling Else inspection.
 */
class RsDanglingElseInspectionTest : RsInspectionsTestBase() {

    fun testSimple() = checkByText<RustDanglingElseInspection>("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else
            if</warning> true {
            }
        }
    """)

    fun testElseOnSeparateLine() = checkByText<RustDanglingElseInspection>("""
        fn main() {
            if true {
            }
            <warning descr="Suspicious `else if` formatting">else
            if</warning> true {
            }
        }
    """)

    fun testMultipleNewLines() = checkByText<RustDanglingElseInspection>("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else


            if</warning> true {
            }
        }
    """)

    fun testComments() = checkByText<RustDanglingElseInspection>("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else

            // comments
            /* inside */

            if</warning> true {
            }
        }
    """)

    fun testNotAppliedWhenNoIf() = checkByText<RustDanglingElseInspection>("""
        fn main() {
            if true {
            } else {
            }
        }
    """)

    fun testNotAppliedWhenNotDangling() = checkByText<RustDanglingElseInspection>("""
        fn main() {
            if true {
            } else if false {
            }
        }
    """)

    fun testFixRemoveElse() = checkFixByText<RustDanglingElseInspection>("Remove `else`", """
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

    fun testFixJoin() = checkFixByText<RustDanglingElseInspection>("Join `else if`", """
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
