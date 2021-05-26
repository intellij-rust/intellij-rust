/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

class MatchPostfixTemplateTest : RsPostfixTemplateTest(MatchPostfixTemplate(RsPostfixTemplateProvider())) {
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
                Message::Quit => {/*caret*/}
                Message::ChangeColor(_, _, _) => {}
                Message::Move { .. } => {}
                Message::Write(_) => {}
            }
        }
    """)

    fun `test constant`() = doTest("""
        const THE_ANSWER: i32 = 42;

        fn check(x: i32) {
            x.match/*caret*/
        }
    """, """
        const THE_ANSWER: i32 = 42;

        fn check(x: i32) {
            match x { _ => {/*caret*/} }
        }
    """)

    fun `test struct literal`() = doTest("""
        struct S {
            a: u32
        }

        fn foo() {
            S { a: 0 }.match/*caret*/
        }
    """, """
        struct S {
            a: u32
        }

        fn foo() {
            match (S { a: 0 }) { S { .. } => {/*caret*/} }
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
                _ => match b { _ => {/*caret*/} }
            }
        }
    """)

    fun `test subexpression`() = doTest("""
        fn main() {
            let x = 1 + 2.match/*caret*/;
        }
    """, """
        fn main() {
            let x = match 1 + 2 { _ => {/*caret*/} };
        }
    """)
}
