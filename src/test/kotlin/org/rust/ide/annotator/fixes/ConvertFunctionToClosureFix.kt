/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class ConvertFunctionToClosureFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test simple 1`() = checkFixByText("Convert function to closure", """
        fn main() {
            let x = 0;
            fn inner() -> i32 {
                <error descr="Can't capture dynamic environment in a fn item [E0434]">x/*caret*/</error>
            }
        }
    """, """
        fn main() {
            let x = 0;
            let inner = || -> i32 {
                x
            };
        }
    """)

    fun `test simple 2`() = checkFixByText("Convert function to closure", """
        fn main(x: i32) {
            fn inner() -> i32 {
                <error descr="Can't capture dynamic environment in a fn item [E0434]">x/*caret*/</error>
            }
        }
    """, """
        fn main(x: i32) {
            let inner = || -> i32 {
                x
            };
        }
    """)

    fun `test nested inner function`() = checkFixIsUnavailable("Convert function to closure", """
        fn main(x: i32) {
            fn inner1() {
                fn inner2() -> i32 {
                    <error descr="Can't capture dynamic environment in a fn item [E0434]">x/*caret*/</error>
                }
            }
        }
    """)

    fun `test method of local impl`() = checkFixIsUnavailable("Convert function to closure", """
        fn main(x: i32) {
            struct Struct {}

            impl Struct {
                fn method(&self) -> i32 {
                    <error descr="Can't capture dynamic environment in a fn item [E0434]">x/*caret*/</error>
                }
            }
        }
    """)
}
