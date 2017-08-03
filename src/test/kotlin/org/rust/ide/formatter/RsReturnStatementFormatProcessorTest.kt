/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsReturnStatementFormatProcessorTest : RsFormatterTestBase() {
    // https://internals.rust-lang.org/t/syntax-of-block-like-expressions-in-match-arms/5025
    fun `test adds semicolon after return statement`() {
        doTextTest("""
            fn main() {
                return
            }

            fn foo() {
                return /* comment */
            }

            fn bar() {
                let mut vector = match iterator.next() {
                    None => return Vec::new(),
                    Some(element) => {}
                };
            }
        """, """
            fn main() {
                return;
            }

            fn foo() {
                return; /* comment */
            }

            fn bar() {
                let mut vector = match iterator.next() {
                    None => return Vec::new(),
                    Some(element) => {}
                };
            }
        """)
    }

    fun `test adds semicolon after return statement with value`() {
        doTextTest("""
            fn foo() -> i32 {
                if true {
                    return 92
                }
                62
            }
        """, """
            fn foo() -> i32 {
                if true {
                    return 92;
                }
                62
            }
        """)
    }
}
