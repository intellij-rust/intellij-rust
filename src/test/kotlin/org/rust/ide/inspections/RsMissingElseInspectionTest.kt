/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

/**
 * Tests for Missing Else inspection.
 */
class RsMissingElseInspectionTest : RsInspectionsTestBase(RsMissingElseInspection()) {

    fun testSimple() = checkByText("""
        fn main() {
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?"> if </warning>true {
            }
        }
    """)

    fun testNoSpaces() = checkByText("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?">if</warning>(a > 10){
            }
        }
    """)

    fun testWideSpaces() = checkByText("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?">   if    </warning>(a > 10) {
            }
        }
    """)

    fun testComments() = checkByText("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?"> /* commented  */ /* else */ if </warning>a > 10 {
            }
        }
    """)

    fun testNotLastExpr() = checkByText("""
        fn main() {
            let a = 10;
            if a > 5 {
            }<warning descr="Suspicious if. Did you mean `else if`?"> if </warning>a > 10{
            }
            let b = 20;
        }
    """)

    fun testHandlesBlocksWithNoSiblingsCorrectly() = checkByText("""
        fn main() {if true {}}
    """)

    fun testNotAppliedWhenLineBreakExists() = checkByText("""
        fn main() {
            if true {}
            if true {}
        }
    """)

    fun testNotAppliedWhenTheresNoSecondIf() = checkByText("""
        fn main() {
            if {
                92;
            }
            {}
        }
    """)

    fun testFix() = checkFixByText("Change to `else if`", """
        fn main() {
            let a = 10;
            if a > 7 {
            }<warning descr="Suspicious if. Did you mean `else if`?"> i<caret>f </warning>a > 14 {
            }
        }
    """, """
        fn main() {
            let a = 10;
            if a > 7 {
            } else if a > 14 {
            }
        }
    """)

    fun testFixPreservesComments() = checkFixByText("Change to `else if`", """
        fn main() {
            let a = 10;
            if a > 7 {
            }<warning descr="Suspicious if. Did you mean `else if`?"> /* comment */<caret> if /* ! */ </warning>a > 14 {
            }
        }
    """, """
        fn main() {
            let a = 10;
            if a > 7 {
            } /* comment */ else if /* ! */ a > 14 {
            }
        }
    """)
}
