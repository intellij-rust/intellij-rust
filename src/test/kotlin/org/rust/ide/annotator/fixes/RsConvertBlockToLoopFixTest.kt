/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsSyntaxErrorsAnnotator

class RsConvertBlockToLoopFixTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test fix`() = checkFixByText("Convert to loop", """
        fn main() {
            'b: {
                /*error*//*caret*/continue 'b/*error**/;
            }
        }
    """, """
        fn main() {
            'b: loop {
                continue 'b;
            }
        }
    """)

}
