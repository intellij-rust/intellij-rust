/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.inspections.RsCastToBoolInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class CompareWithZeroFixTest : RsInspectionsTestBase(RsCastToBoolInspection::class) {

    fun `test fix numeric cast to bool`() = checkFixByText("Compare with zero", """
        fn main() {
            <error descr="It is not allowed to cast to a bool. [E0054]">5 as bool</error>/*caret*/;
        }
    """, """
        fn main() {
            5 != 0/*caret*/;
        }
    """)

    fun `test fix numeric cast to bool in expression`() = checkFixByText("Compare with zero", """
        fn main() {
            let _ = !(<error descr="It is not allowed to cast to a bool. [E0054]">5 as bool</error>/*caret*/);
        }
    """, """
        fn main() {
            let _ = !(5 != 0/*caret*/);
        }
    """)

    // The compare with zero fix does not apply to casts from types that can not be compared with zero.
    fun `test absent fix nonnumerical cast to bool`() = checkFixIsUnavailable("Compare with zero", """
        fn main() {
            <error descr="It is not allowed to cast to a bool. [E0054]">'a' as bool</error>/*caret*/;
        }
    """)

}
