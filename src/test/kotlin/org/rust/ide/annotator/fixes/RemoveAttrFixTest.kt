/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class RemoveAttrFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class.java) {

    fun `test repr on empty enum`() = checkFixByText("Remove attribute `repr`", """
        #[<error descr="Enum with no variants can't have `repr` attribute [E0084]">repr/*caret*/</error>(u8)]
        enum Test {}
    """, """
        enum Test {}
    """)

}
