/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class UnwrapSingleExprIntentionTest : RsIntentionTestBase(UnwrapSingleExprIntention::class) {
    fun `test available lambda unwrap braces single expression`() = doAvailableTest("""
        fn main() {
            {
                4/*caret*/2
            }
        }
    """, """
        fn main() {
            4/*caret*/2
        }
    """)

    fun `test available lambda unwrap braces`() = doAvailableTest("""
        fn main() {
            |x| { x */*caret*/ x }
        }
    """, """
        fn main() {
            |x| x */*caret*/ x
        }
    """)

    fun `test available unwrap braces single expression if`() = doAvailableTest("""
        fn main() {
            let a = {
                if (true) {
                    42
                } else/*caret*/ {
                    43
                }
            };
        }
    """, """
        fn main() {
            let a = if (true) {
                42
            } else/*caret*/ {
                43
            };
        }
    """)

    fun `test available lambda unwrap braces single statement`() = doUnavailableTest("""
        fn main() {
            {
                /*caret*/42;
            }
        }
    """)

    fun `test unavailable unwrap braces`() = doUnavailableTest("""
        fn main() {
            |x| { let a = 3; x */*caret*/ a
            }
    """)

    fun `test unavailable unwrap braces let`() = doUnavailableTest("""
        fn main() {
            {
                /*caret*/let a = 5;
            }
        }
    """)

    fun `test unavailable unwrap braces unsafe`() = doUnavailableTest("""
        fn main() {
            let wellThen = unsafe/*caret*/ { magic() };
        }
    """)

    fun `test unavailable unwrap braces async`() = doUnavailableTest("""
        fn main() {
            let wellThen = async/*caret*/ { magic() };
        }
    """)

    fun `test unavailable unwrap braces try`() = doUnavailableTest("""
        fn main() {
            let wellThen = try/*caret*/ { magic() };
        }
    """)

    fun `test available unwrap braces single expression match`() = doAvailableTest("""
        fn main() {
            match x {
                0 => {
                    prin/*caret*/tln!("x = 0")
                }
            }
        }
    """, """
        fn main() {
            match x {
                0 => prin/*caret*/tln!("x = 0"),
            }
        }
    """)

    fun `test available unwrap braces match with caret before`() = doAvailableTest("""
        fn main() {
            match x {
                0 => /*caret*/{
                    println!("x = 0")
                }
            }
        }
    """, """
        fn main() {
            match x {
                0 => /*caret*/println!("x = 0"),
            }
        }
    """)

    fun `test available unwrap braces match with caret after`() = doAvailableTest("""
        fn main() {
            match x {
                0 => {
                    println!("x = 0")
                }/*caret*/
            }
        }
    """, """
        fn main() {
            match x {
                0 => println!("x = 0")/*caret*/,
            }
        }
    """)

    fun `test available unwrap braces multiple expression match`() = doAvailableTest("""
        fn main() {
            match x {
                0 => /*caret*/{
                    println!("x = 0")
                }
                _ => println!("x != 0")
            }
        }
    """, """
        fn main() {
            match x {
                0 => /*caret*/println!("x = 0"),
                _ => println!("x != 0")
            }
        }
    """)
}
