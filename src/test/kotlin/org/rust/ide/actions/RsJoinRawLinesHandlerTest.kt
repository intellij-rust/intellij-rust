/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

class RsJoinRawLinesHandlerTest : RsJoinLinesHandlerTestBase() {
    fun `test block expression`() = doTest("""
        fn main() {
            let _ = /*caret*/{
                42
            };
        }
    """, """
        fn main() {
            let _ = /*caret*/42;
        }
    """)

    fun `test match expression last arm`() = doTest("""
        fn main() {
            match n {
                1 => /*caret*/{
                    ()
                }
                2 => {
                    ()
                }
            }
        }
    """, """
        fn main() {
            match n {
                1 => (),
                2 => {
                    ()
                }
            }
        }
    """)

    fun `test match block arm with comma`() = doTest("""
        fn main() {
            match current.parent() {
                Some(p) => /*caret*/{
                    current = p
                },
                None => break,
            }
        }
    """, """
        fn main() {
            match current.parent() {
                Some(p) => current = p,
                None => break,
            }
        }
    """)

    fun `test lambda expression`() = doTest("""
        fn main() {
            let _: Vec<()> = xs.iter()
              /*caret*/.map(|x| {
                x*x
              }).collect();
        }
    """, """
        fn main() {
            let _: Vec<()> = xs.iter()
              .map(|x| /*caret*/x*x).collect();
        }
    """)

    fun `test don't loose comments`() = doTest("""
        fn main() {
            let _ = /*caret*/{
                // The ultimate answer
                92
            };
        }
    """, """
        fn main() {
            let _ = {/*caret*/ // The ultimate answer
                92
            };
        }
    """)

    fun `test if`() = doTest("""
        fn main() {
            if true /*caret*/{
                92
            } else {
                62
            }
        }
    """, """
        fn main() {
            if true /*caret*/{ 92 } else {
                62
            }
        }
    """)

    fun `test if else`() = doTest("""
        fn main() {
            if true /*caret*/{ 92 } else {
                62
            }
        }
    """, """
        fn main() {
            if true { 92 } else /*caret*/{ 62 }
        }
    """)
}
