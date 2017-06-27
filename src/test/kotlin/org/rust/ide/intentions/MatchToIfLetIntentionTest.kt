/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class MatchToIfLetIntentionTest : RsIntentionTestBase(MatchToIfLetIntention()) {
    fun `test unavailable all void arms`() = doUnavailableTest("""
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
    """)

    fun `test unavailable all not void arms`() = doUnavailableTest("""
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
    """)

    fun `test unavailable pattern`() = doUnavailableTest("""
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
    """)

    fun `test simple 1`() = doAvailableTest("""
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
    """, """
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
    """)

    fun `test simple 2`() = doAvailableTest("""
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
    """, """
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
    """)
}
