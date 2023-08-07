/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsContinueLabelPointToBlockTest: RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0696 point to block`() = checkByText("""
        fn main() {
            'b: {
                /*error descr="`continue` pointing to a labeled block [E0696]"*/continue 'b/*error**/;
            }
        }
    """)

    fun `test E0696 point to loop`() = checkByText("""
        fn main() {
            'b: loop {
                continue 'b;
            }
        }
    """)
}
