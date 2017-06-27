/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class MoveGuardToMatchArmIntentionTest : RsIntentionTestBase(MoveGuardToMatchArmIntention()) {
    fun `test unavailable without match arm`() = doUnavailableTest("""
        fn main() {
            match 1 {
                1 if /*caret*/ true =>
            }
        }
    """)

    fun `test block body`() = doAvailableTest("""
        fn main() {
            match 1 {
                1 if /*caret*/true => {}
            }
        }
    """, """
        fn main() {
            match 1 {
                1 => if /*caret*/true {}
            }
        }
    """)

    fun `test block body with comma`() = doAvailableTest("""
        fn main() {
            match 1 {
                1 if true/*caret*/ => {
                    let x = 92;
                    println!("{}", x);
                },
            }
        }
    """, """
        fn main() {
            match 1 {
                1 => if true/*caret*/ {
                    let x = 92;
                    println!("{}", x);
                },
            }
        }
    """)

    fun `test expression body`() = doAvailableTest("""
        fn main() {
            match 1 {
                2 => 8,
                1 if /*caret*/ 1 + 2 < 2 => 3 + 4
            }
        }
    """, """
        fn main() {
            match 1 {
                2 => 8,
                1 => if 1 + 2 < 2 { 3 + 4 }
            }
        }
    """)

    fun `test expression body with comma`() = doAvailableTest("""
        fn main() {
            match 1 {
                1 if /*caret*/ true => (),
                2 => ()
            }
        }
    """, """
        fn main() {
            match 1 {
                1 => if true { () },
                2 => ()
            }
        }
    """)
}
