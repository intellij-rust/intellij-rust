/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.MockRustcVersion
import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class ReplaceBoxSyntaxFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    @MockRustcVersion("1.70.0")
    fun `test replace box syntax`() = checkFixByText("Replace `box` with `Box::new`", """
        struct S;
        fn main() {
            let x = <error descr="`box` expression syntax has been removed">/*caret*/box</error> S;
        }
    """, """
        struct S;
        fn main() {
            let x = /*caret*/Box::new(S);
        }
    """)
}
