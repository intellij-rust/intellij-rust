/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsMatchArmCommaFormatProcessorTest : RsFormatterTestBase() {
    // https://internals.rust-lang.org/t/syntax-of-block-like-expressions-in-match-arms/5025
    fun `test removes commas in match arms with blocks`() = doTextTest("""
        fn main() {
            match x {
                1 => 1,
                2 => { 2 },
                3 => { 3 }
                92 => unsafe { 3 },
                4 => loop {},
                5 => 5,
                6 => if true {} else {},
                7 => 7
            }
        }
    """, """
        fn main() {
            match x {
                1 => 1,
                2 => { 2 }
                3 => { 3 }
                92 => unsafe { 3 },
                4 => loop {},
                5 => 5,
                6 => if true {} else {},
                7 => 7
            }
        }
    """)
}
