/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsSyntaxErrorsAnnotator

class RsAddLabelFixTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test fix`() = checkFixByText("Add label", """
        fn main() {
            while <error>/*caret*/break</error> {}
        }
    """, """
        fn main() {
            'a: while break 'a {}
        }
    """)

}
