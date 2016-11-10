package org.rust.ide.template.postfix

/**
 * Created by chainic-vina on 10.11.16.
 */
class MatchPostfixTemplateTest : PostfixTemplateTestCase(MatchPostfixTemplate()) {
//    TODO: Should be not applicable
//    fun testTypename() = doTestNotApplicable(
//        """
//            enum Message {
//                Quit,
//                ChangeColor(i32, i32, i32),
//                Move { x: i32, y: i32 },
//                Write(String),
//            }
//
//            fn process_message(msg: Message) {
//                Message.match/*caret*/
//            }
//        """
//    )

    fun testSimple() = doTest(
        """
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
        """
        ,
        """
        enum Message {
            Quit,
            ChangeColor(i32, i32, i32),
            Move { x: i32, y: i32 },
            Write(String),
        }

        fn process_message() {
            let msg = Message::ChangeColor(255, 255, 255);
            match msg {
                Message::Quit => (),
                Message::ChangeColor(v0, v1, v2) => (),
                Message::Move { x, y } => (),
                Message::Write(v0) => (),
            };
        }
        """
    )

    fun testFn() = doTest(
        """
        enum Option<T> {
            None,
            Some(T)
        }

        fn divide(numerator: f64, denominator: f64) -> Option<f64> {
            if denominator == 0.0 {
                None
            } else {
                Some(numerator / denominator)
            }
        }

        fn main() {
            divide(1, 2).match/*caret*/
        }
        """
        ,
        """
        enum Option<T> {
            None,
            Some(T)
        }

        fn divide(numerator: f64, denominator: f64) -> Option<f64> {
            if denominator == 0.0 {
                None
            } else {
                Some(numerator / denominator)
            }
        }

        fn main() {
            match divide(1, 2) {
                Option::None => (),
                Option::Some(v0) => (),
            };
        }
        """
    )

    fun testLet() = doTest(
        """
        enum Option<T> {
            None,
            Some(T)
        }

        fn foo() {
            let v = Option::Some(42);
            v.match/*caret*/
        }
        """
        ,
        """
        enum Option<T> {
            None,
            Some(T)
        }

        fn foo() {
            let v = Option::Some(42);
            match v {
                Option::None => (),
                Option::Some(v0) => (),
            };
        }
        """
    )
}
