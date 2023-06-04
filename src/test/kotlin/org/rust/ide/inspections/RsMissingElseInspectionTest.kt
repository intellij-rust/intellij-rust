/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.SkipTestWrapping

/**
 * Tests for Missing Else inspection.
 */
@SkipTestWrapping // TODO adjust `fixupRustSyntaxErrors`
class RsMissingElseInspectionTest : RsInspectionsTestBase(RsMissingElseInspection::class) {

    fun `test simple`() = checkByText("""
        fn main() {
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?"> if </warning>true {
            }
        }
    """)

    fun `test no spaces`() = checkByText("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?">if</warning>(a > 10){
            }
        }
    """)

    fun `test wide spaces`() = checkByText("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?">   if    </warning>(a > 10) {
            }
        }
    """)

    fun `test comments`() = checkByText("""
        fn main() {
            let a = 10;
            if true {
            }<warning descr="Suspicious if. Did you mean `else if`?"> /* commented  */ /* else */ if </warning>a > 10 {
            }
        }
    """)

    fun `test not last expr`() = checkByText("""
        fn main() {
            let a = 10;
            if a > 5 {
            }<warning descr="Suspicious if. Did you mean `else if`?"> if </warning>a > 10{
            }
            let b = 20;
        }
    """)

    fun `test handles blocks with no siblings correctly`() = checkByText("""
        fn main() {if true {}}
    """)

    fun `test not applied when line break exists`() = checkByText("""
        fn main() {
            if true {}
            if true {}
        }
    """)

    fun `test not applied when theres no secondIf`() = checkByText("""
        fn main() {
            if {
                92;
            }
            {}
        }
    """)

    fun `test fix`() = checkFixByText("Change to `else if`", """
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

    fun `test fix preserves comments`() = checkFixByText("Change to `else if`", """
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
