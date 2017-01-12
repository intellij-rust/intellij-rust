package org.rust.ide.intentions

class MatchToIfLetIntentionTest : RsIntentionTestBase(MatchToIfLetIntention()) {
    fun testUnavailableAllVoidArms() = doUnavailableTest(
        """
        enum MyOption {
            Nothing,
            Some(x),
        }

        fn main() {
            let a = MyOption::Some(52);

            match a {
                MyOption::Some(x) => {},/*caret*/
                Nothing => {}
            }
        }
        """
    )

    fun testUnavailableAllNotVoidArms() = doUnavailableTest(
        """
        enum MyOption {
            Nothing,
            Some(x),
        }

        fn main() {
            let a = MyOption::Some(52);

            match a {
                MyOption::Some(x) => {42},
                Nothing => {43}/*caret*/
            }
        }
        """
    )

    fun testUnavailable_pattern() = doUnavailableTest(
        """
        enum OptionColor {
            NoColor,
            Color(i32, i32, i32),
        }

        fn main() {
            let color = OptionColor::Color(255, 255, 255);

            match color {/*caret*/
                OptionColor::Color(_, _, _) => {},
                _ => {print!("No color")},
            };
        }
        """
    )

    fun testSimple1() = doAvailableTest(
        """
        enum MyOption {
            Nothing,
            Some(x),
        }

        fn main() {
            let color = MyOption::Some(52);

            match color {
                MyOption::Some(x) => {
                    let a = x + 1;
                    let b = x + 2;
                    let c = a + b;
                },/*caret*/
                Nothing => {}
            }
        }
        """
        ,
        """
        enum MyOption {
            Nothing,
            Some(x),
        }

        fn main() {
            let color = MyOption::Some(52);

            if let MyOption::Some(x) = color {
                let a = x + 1;
                let b = x + 2;
                let c = a + b;
            }
        }
        """
    )

    fun testSimple2() = doAvailableTest(
        """
        enum OptionColor {
            NoColor,
            Color(i32, i32, i32),
        }

        fn main() {
            let color = OptionColor::Color(255, 255, 255);

            match color {/*caret*/
                OptionColor::Color(255, 255, 255) => print!("White"),
                OptionColor::Color(_,   _,   _  ) => {},
                OptionColor::NoColor => {},
            };
        }
        """
        ,
        """
        enum OptionColor {
            NoColor,
            Color(i32, i32, i32),
        }

        fn main() {
            let color = OptionColor::Color(255, 255, 255);

            if let OptionColor::Color(255, 255, 255) = color {
                print!("White")
            };
        }
        """
    )
}
