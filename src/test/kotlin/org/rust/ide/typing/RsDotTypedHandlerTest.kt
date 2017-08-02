/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing

class RsDotTypedHandlerTest : RsTypingTestBase() {
    fun `test autoindent dot in chained call`() = doTestByText("""
        fn main() {
            frobnicate()
                .foo()
            /*caret*/
        }
    """, """
        fn main() {
            frobnicate()
                .foo()
                ./*caret*/
        }
    """, '.')

    fun `test test autoindent dot with deep indent`() = doTestByText("""
        fn main() {
            let matches =
                App::new("Unique Random File Generator")
                    .version("1.0")
            /*caret*/
        }
    """, """
        fn main() {
            let matches =
                App::new("Unique Random File Generator")
                    .version("1.0")
                    ./*caret*/
        }
    """, '.')
}
