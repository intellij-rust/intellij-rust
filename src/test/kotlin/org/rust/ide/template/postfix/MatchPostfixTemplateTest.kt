package org.rust.ide.template.postfix

class MatchPostfixTemplateTest : PostfixTemplateTest(MatchPostfixTemplate()) {
    fun testTypename() = doTestNotApplicable(
        """
            enum Message {
                Quit,
                Write(String),
            }

            fn process_message(msg: Message) {
                Message.match/*caret*/
            }
        """
    )

    fun testNotApplicable() = doTestNotApplicable(
        """
        fn main() {
            42.match/*caret*/
        }
        """
    )

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
                Message::Quit => {},
                Message::ChangeColor(v0, v1, v2) => {},
                Message::Move { x, y } => {},
                Message::Write(v0) => {},
            };
        }
        """
    )

    fun testUse() = doTest(
        """
        enum Message {
            Quit,
            ChangeColor(i32, i32, i32),
            Move { x: i32, y: i32 },
            Write(String),
        }

        fn process_message() {
            let msg = Message::ChangeColor(255, 255, 255);
            use Message::*;
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
            use Message::*;
            match msg {
                Quit => {},
                ChangeColor(v0, v1, v2) => {},
                Move { x, y } => {},
                Write(v0) => {},
            };
        }
        """
    )

    fun testUse2() = doTest(
        """
        enum Option<T> {
            None,
            Some(T)
        }

        fn main() {
            let a = Option::Some(42);
            use Option::None;
            a.match/*caret*/
        }
        """
        ,
        """
        enum Option<T> {
            None,
            Some(T)
        }

        fn main() {
            let a = Option::Some(42);
            use Option::None;
            match a {
                Option::None => {},
                Option::Some(v0) => {},
            };
        }
        """
    )

    fun testFullPath() = doTest(
        """
        mod BarMod {
            mod FooMod {
                enum Option<T> {
                    None,
                    Some(T)
                }
            }
        }

        fn main() {
            let a = BarMod::FooMod::Option::Some(54);
            a.match/*caret*/
        }
        """
        ,
        """
        mod BarMod {
            mod FooMod {
                enum Option<T> {
                    None,
                    Some(T)
                }
            }
        }

        fn main() {
            let a = BarMod::FooMod::Option::Some(54);
            match a {
                ::BarMod::FooMod::Option::None => {},
                ::BarMod::FooMod::Option::Some(v0) => {},
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
                Option::None => {},
                Option::Some(v0) => {},
            };
        }
        """
    )

    fun testSimple2() = doTest(
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
                Option::None => {},
                Option::Some(v0) => {},
            };
        }
        """
    )

    fun testAlias() = doTest(
        """
        mod m {
            pub enum E {
                X
            }
        }

        use self::m::E as S;

        fn main() {
            S::X.match/*caret*/
        }
        """
        ,
        """
        mod m {
            pub enum E {
                X
            }
        }

        use self::m::E as S;

        fn main() {
            match S::X {
                ::m::E::X => {},
            };
        }
        """
    )
}
