/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

class RsMatchArmUpDownMoverTest : RsStatementUpDownMoverTestBase() {
    fun `test step over match arm single line expr`() = moveDownAndBackUp("""
        fn main() {
            match test {
                Some(/*caret*/_) => (),
                None => (),
            }
        }
    """, """
        fn main() {
            match test {
                None => (),
                Some(/*caret*/_) => (),
            }
        }
    """)

    fun `test step over match arm multi line expr`() = moveDownAndBackUp("""
        fn main() {
            match test {
                Some(/*caret*/_) => {

                }
                None => {

                }
            }
        }
    """, """
        fn main() {
            match test {
                None => {

                }
                Some(/*caret*/_) => {

                }
            }
        }
    """)

    fun `test prevent step out match body`() = moveDownAndBackUp("""
        fn main() {
            match test {
                Some(/*caret*/_) => {

                }
            }
        }
    """, testmark = UpDownMoverTestMarks.moveOutOfMatch)

    fun `test can move several arms`() = moveDownAndBackUp("""
        fn main() {
            match x {
                <selection>1 => {},
                2 => loop {}</selection>

                3 => {},
            }
        }
    """, """
        fn main() {
            match x {
                3 => {},

                <selection>1 => {},
                2 => loop {}</selection>
            }
        }
    """)

    fun `test can not move across different match expressions`() = moveDownAndBackUp("""
        fn main() {
            match x {
                1 => {},
                <selection>2 => {},
            };
            match x {
                2 => {},</selection>
                3 => {},
            };
        }
    """, """
        fn main() {
            match x {
                1 => {},
                <selection>2 => {},
            };
            match x {
                2 => {},</selection>
                3 => {},
            };
        }
    """)
}
