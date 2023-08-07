/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for Dangling Else inspection.
 */
class RsDanglingElseInspectionTest : RsInspectionsTestBase(RsDanglingElseInspection::class) {

    fun `test simple`() = checkByText("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else
            if</warning> true {
            }
        }
    """)

    fun `test else on separate line`() = checkByText("""
        fn main() {
            if true {
            }
            <warning descr="Suspicious `else if` formatting">else
            if</warning> true {
            }
        }
    """)

    fun `test multiple new lines`() = checkByText("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else


            if</warning> true {
            }
        }
    """)

    fun `test comments`() = checkByText("""
        fn main() {
            if true {
            } <warning descr="Suspicious `else if` formatting">else

            // comments
            /* inside */

            if</warning> true {
            }
        }
    """)

    fun `test not applied when no if`() = checkByText("""
        fn main() {
            if true {
            } else {
            }
        }
    """)

    fun `test not applied when not dangling`() = checkByText("""
        fn main() {
            if true {
            } else if false {
            }
        }
    """)

    fun `test fix remove else`() = checkFixByText("Remove `else`", """
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

    fun `test fix join`() = checkFixByText("Join `else if`", """
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
