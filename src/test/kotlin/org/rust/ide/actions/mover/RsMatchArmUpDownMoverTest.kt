/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

class RsMatchArmUpDownMoverTest : RsStatementUpDownMoverTestBase() {

    fun `test step over match arm single line expr`() = moveBothDirectionTest("""
            // - main.rs
            fn main() {
                match test {
                    Some(/*caret*/_) => (),
                    None => (),
                }
            }
        """, """
            // - main.rs
            fn main() {
                match test {
                    None => (),
                    Some(/*caret*/_) => (),
                }
            }
        """)

    fun `test step over match arm multi line expr`() = moveBothDirectionTest("""
            // - main.rs
            fn main() {
                match test {
                    Some(/*caret*/_) => {

                    },
                    None => {

                    },
                }
            }
        """, """
            // - main.rs
            fn main() {
                match test {
                    None => {

                    },
                    Some(/*caret*/_) => {

                    },
                }
            }
        """)

    fun `test prevent step out match body`() = moveBothDirectionTest("""
            // - main.rs
            fn main() {
                match test {
                    Some(/*caret*/_) => {

                    },
                }
            }
        """, """
            // - main.rs
            fn main() {
                match test {
                    Some(/*caret*/_) => {

                    },
                }
            }
        """)
}
