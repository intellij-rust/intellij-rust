/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class AddAttrParenthesesFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    // Note: this tests both the fix and it moving the caret to the parentheses.
    fun `test fix repr without parentheses`() = checkFixByText("Add parentheses to `repr`", """
        #[<error descr="Malformed `repr` attribute input: missing parentheses">re/*caret*/pr</error>]
        enum E {
            V
        }
    """, """
        #[repr(/*caret*/)]
        enum E {
            V
        }
    """)

}
