/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsStatementSemicolonFormatProcessorTest : RsFormatterTestBase() {
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

    fun `test adds semicolon after break`() {
        doTextTest("""
            fn foo(cond: bool) {
                loop {
                    if cond {
                        break
                    }
                }
                loop {
                    if cond {
                        break;
                    }
                }
                'label: loop {
                    if cond {
                        break 'label
                    }
                }
            }
        """, """
            fn foo(cond: bool) {
                loop {
                    if cond {
                        break;
                    }
                }
                loop {
                    if cond {
                        break;
                    }
                }
                'label: loop {
                    if cond {
                        break 'label;
                    }
                }
            }
        """)
    }

    fun `test adds semicolon after continue`() {
        doTextTest("""
            fn foo(cond: bool) {
                loop {
                    if cond {
                        continue
                    }
                }
                loop {
                    if cond {
                        continue;
                    }
                }
                'label: loop {
                    if cond {
                        continue 'label
                    }
                }
            }
        """, """
            fn foo(cond: bool) {
                loop {
                    if cond {
                        continue;
                    }
                }
                loop {
                    if cond {
                        continue;
                    }
                }
                'label: loop {
                    if cond {
                        continue 'label;
                    }
                }
            }
        """)
    }

    fun `test does not add redundant semicolon`() {
        val code = """
        fn main() {
            loop {
                match Some(0) {
                    Some(v) => break Some(v),
                    None => break Some(0)
                }
            }
        }
    """;
        checkNotChanged(code)

    }
}
