/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.intellij.lang.annotations.Language

class RsWhileTrueLoopInspectionTest : RsInspectionsTestBase(RsWhileTrueLoopInspection()) {
    fun `test simple`() = checkFix("""
        fn main() {
            <weak_warning descr="Denote infinite loops with `loop { ... }`">while/*caret*/ true</weak_warning> {
                let a = 42;
                println!("{}", a);
            }
        }
    """, """
        fn main() {
            loop {
                let a = 42;
                println!("{}", a);
            }
        }
    """)

    fun `test with label`() = checkFix("""
        fn main() {
            'outer: <weak_warning descr="Denote infinite loops with `loop { ... }`">while/*caret*/ true</weak_warning> {
                let mut _a = 0;
                while _a < 10 {
                    _a += 1;
                    if _a == 3 {
                        break 'outer;
                    }
                }
            }
        }
    """, """
        fn main() {
            'outer: loop {
                let mut _a = 0;
                while _a < 10 {
                    _a += 1;
                    if _a == 3 {
                        break 'outer;
                    }
                }
            }
        }
    """)

    fun `test with unnecessary parentheses around while`() = checkFix("""
        fn main() {
            <weak_warning descr="Denote infinite loops with `loop { ... }`">while/*caret*/ (true)</weak_warning> {
                let a = 42;
                println!("{}", a);
            }
        }
    """, """
        fn main() {
            loop {
                let a = 42;
                println!("{}", a);
            }
        }
    """)

    private fun checkFix(
        @Language("Rust") before: String,
        @Language("Rust") after: String
    ) = checkFixByText(
        "Use `loop`",
        before,
        after,
        checkWeakWarn = true
    )
}
