/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsBreakContinueWithoutLabelInWhileConditionTest: RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0590 continue without label`() = checkByText("""
        fn main() {
            while /*error descr="`continue` with no label in the condition of a `while` loop [E0590]"*/continue/*error**/ {}
        }
    """)

    fun `test E0590 break without label`() = checkByText("""
        fn main() {
            while <error descr="`break` with no label in the condition of a `while` loop [E0590]">break</error> {}
        }
    """)

    fun `test E0590 continue with label`() = checkByText("""
        fn main() {
            'a: while continue 'a {}
        }
    """)

    fun `test E0590 break with label`() = checkByText("""
        fn main() {
            'a: while break 'a {}
        }
    """)

    fun `test E0590 continue without label inside body`() = checkByText("""
        fn main() {
            while true {
                continue;
            }
        }
    """)

    fun `test E0590 break without label inside body`() = checkByText("""
        fn main() {
            while true {
                break;
            }
        }
    """)
}
