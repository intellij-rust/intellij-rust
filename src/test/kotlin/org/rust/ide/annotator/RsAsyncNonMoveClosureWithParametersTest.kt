/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsAsyncNonMoveClosureWithParametersTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0708 async non-move closure with parameters`() = checkByText("""
        fn main() {
            let add_one = <error descr="async non-move closures with parameters are currently not supported [E0708]">async |num: u8|</error> {
                num + 1
            };
        }
    """)

    fun `test E0708 async move closure with parameters`() = checkByText("""
        fn main() {
            let add_one = async move |num: u8| {
                num + 1;
            };
        }
    """.trimIndent())


    fun `test E0708 non-async non-move closure with parameters`() = checkByText("""
        fn main() {
            let add_one = |num: u8| {
                num + 1;
            };
        }
    """.trimIndent())

    fun `test E0708 non-async move closure with parameters`() = checkByText("""
        fn main() {
            let add_one = move |num: u8| {
                num + 1;
            };
        }
    """.trimIndent())


    fun `test E0708 async non-move closure without parameters`() = checkByText("""
        fn main() {
            let one = || {
                1;
            };
        }
    """.trimIndent())


    fun `test E0708 async move block`() = checkByText("""
        fn main() {
            let num = 1;
            let ret_num = async move {
                num;
            };
        }
    """.trimIndent())


    fun `test E0708 async block`() = checkByText("""
        fn main() {
            let ret_num = async {
                1;
            };
        }
    """.trimIndent())
}
