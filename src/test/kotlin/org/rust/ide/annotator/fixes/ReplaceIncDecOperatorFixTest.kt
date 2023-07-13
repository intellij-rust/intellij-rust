/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsSyntaxErrorsAnnotator

class ReplaceIncDecOperatorFixTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {

    fun `test prefix increment operator top expr`() = checkFixByText("Replace with `+= 1`", """
        fn main() {
            let mut a = 0;
            <error>/*caret*/++</error>a;
        }
    """, """
        fn main() {
            let mut a = 0;
            a += 1;
        }
    """)

    fun `test postfix increment operator top expr`() = checkFixByText("Replace with `+= 1`", """
        fn main() {
            let mut a = 0;
            a<error>/*caret*/++</error>;
        }
    """, """
        fn main() {
            let mut a = 0;
            a += 1;
        }
    """)

    fun `test postfix decrement operator top expr`() = checkFixByText("Replace with `-= 1`", """
        fn main() {
            let mut a = 0;
            a<error>/*caret*/--</error>;
        }
    """, """
        fn main() {
            let mut a = 0;
            a -= 1;
        }
    """)

    fun `test prefix increment operator nested expr`() = checkFixIsUnavailable("Replace with `+= 1`", """
        fn main() {
            let mut a = 0;
            <error>/*caret*/++</error>a < 1;
        }
    """)

    fun `test postfix increment operator nested expr`() = checkFixIsUnavailable("Replace with `+= 1`", """
        fn main() {
            let mut a = 0;
            a<error>/*caret*/++</error> < 1;
        }
    """)

    fun `test prefix increment operator expr with attrs`() = checkFixIsUnavailable("Replace with `+= 1`", """
        fn main() {
            let mut a = 0;
            #[attr] <error>/*caret*/++</error>a;
        }
    """)

    fun `test postfix increment operator expr with attrs`() = checkFixIsUnavailable("Replace with `+= 1`", """
        fn main() {
            let mut a = 0;
            #[attr] a<error>/*caret*/++</error>;
        }
    """)

    fun `test postfix decrement operator expr with attrs`() = checkFixIsUnavailable("Replace with `-= 1`", """
        fn main() {
            let mut a = 0;
            #[attr] a<error>/*caret*/--</error>;
        }
    """)
}
