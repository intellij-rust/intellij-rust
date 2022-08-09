/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import org.rust.CheckTestmarkHit

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

    @CheckTestmarkHit(UpDownMoverTestMarks.MoveOutOfMatch::class)
    fun `test prevent step out match body`() = moveDownAndBackUp("""
        fn main() {
            match test {
                Some(/*caret*/_) => {

                }
            }
        }
    """)

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

    fun `test inside closure`() = moveUp("""
        fn main() {
            let xs: Vec<_> = vec![1,2,3].iter().map(|x| {
                match x {
                    1 => 10,
                    2 => 20,/*caret*/
                    _ => 30
                }
            }).collect();
        }
    """, """
        fn main() {
            let xs: Vec<_> = vec![1,2,3].iter().map(|x| {
                match x {
                    2 => 20,/*caret*/
                    1 => 10,
                    _ => 30
                }
            }).collect();
        }
    """)

    fun `test move to last`() = moveDown("""
        fn main() {
            match x {
                1 => 10,
                2 => 20,/*caret*/
                _ => 30,
            };
        }
    """, """
        fn main() {
            match x {
                1 => 10,
                _ => 30,
                2 => 20,/*caret*/
            };
        }
    """)

    fun `test move to last 2`() = moveDown("""
        fn main() {
            match x {
                1 => 10,
                2 => 20,/*caret*/
                _ => 30
            };
        }
    """, """
        fn main() {
            match x {
                1 => 10,
                _ => 30,
                2 => 20,/*caret*/
            };
        }
    """)

    fun `test move to last 3`() = moveDown("""
        fn main() {
            match x {
                1 => 10,
                2 => 20,/*caret*/
                _ => {
                    30;
                }
            };
        }
    """, """
        fn main() {
            match x {
                1 => 10,
                _ => {
                    30;
                }
                2 => 20,/*caret*/
            };
        }
    """)

    fun `test move from last`() = moveUp("""
        fn main() {
            match x {
                1 => 10,
                2 => 20,
                _ => 30,/*caret*/
            };
        }
    """, """
        fn main() {
            match x {
                1 => 10,
                _ => 30,/*caret*/
                2 => 20,
            };
        }
    """)

    fun `test move from last 2`() = moveUp("""
        fn main() {
            match x {
                1 => 10,
                2 => 20,
                _ => 30/*caret*/
            };
        }
    """, """
        fn main() {
            match x {
                1 => 10,
                _ => 30/*caret*/,
                2 => 20,
            };
        }
    """)

    fun `test move from last 3`() = moveUp("""
        fn main() {
            match x {
                1 => 10,
                2 => 20,
                _ => {/*caret*/
                    30;
                }
            };
        }
    """, """
        fn main() {
            match x {
                1 => 10,
                _ => {/*caret*/
                    30;
                }
                2 => 20,
            };
        }
    """)

    fun `test move from last 4`() = moveUp("""
        fn foo() {
            match x {
                1 => 10,
                2 => {
                    20;
                    30;
                }
                _ => 40,/*caret*/
            };
        }
    """, """
        fn foo() {
            match x {
                1 => 10,
                _ => 40,/*caret*/
                2 => {
                    20;
                    30;
                }
            };
        }
    """)

    fun `test move first down`() = moveDown("""
        fn foo() {
            match x {
                1 => 10,/*caret*/
                2 => {
                    20;
                    30;
                }
                _ => 40,
            };
        }
    """, """
        fn foo() {
            match x {
                2 => {
                    20;
                    30;
                }
                1 => 10,/*caret*/
                _ => 40,
            };
        }
    """)
}
