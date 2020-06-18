/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class RemoveReprValueFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test fix E0517 remove wrong repr value`() = checkFixByText("Remove", """
        #[repr(<error descr="C attribute should be applied to struct, enum, or union [E0517]">C/*caret*/</error>)]
        type Test = i32;
    """, """
        #[repr()]
        type Test = i32;
    """)

    fun `test fix E0552 unrecognized repr`() = checkFixByText("Remove", """
        #[repr(<error descr="Unrecognized representation CD [E0552]">CD/*caret*/</error>)]
        struct Test(i32);
    """, """
        #[repr()]
        struct Test(i32);
    """)
}
