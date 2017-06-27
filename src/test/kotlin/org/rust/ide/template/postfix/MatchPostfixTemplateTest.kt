/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class MatchPostfixTemplateTest : PostfixTemplateTest(MatchPostfixTemplate()) {
    fun `test simple`() = doTest("""
        enum Message {
            Quit,
            ChangeColor(i32, i32, i32),
            Move { x: i32, y: i32 },
            Write(String),
        }

        fn process_message() {
            let msg = Message::ChangeColor(255, 255, 255);
            msg.match/*caret*/
        }
    """, """
        enum Message {
            Quit,
            ChangeColor(i32, i32, i32),
            Move { x: i32, y: i32 },
            Write(String),
        }

        fn process_message() {
            let msg = Message::ChangeColor(255, 255, 255);
            match msg {
                _ => {}
            }
        }
    """)

    fun `test can match constant`() = doTest("""
        const THE_ANSWER: i32 = 42;

        fn check(x: i32) -> bool {
            x.match/*caret*/
        }
    """, """
        const THE_ANSWER: i32 = 42;

        fn check(x: i32) -> bool {
            match x {
                _ => {}
            }
        }
    """)

    fun `test nested`() = doTest("""
        fn main() {
            match a {
                _ => b.match/*caret*/
            }
        }
    """, """
        fn main() {
            match a {
                _ => match b {
                    _ => {/*caret*/}
                }
            }
        }
    """)

}
