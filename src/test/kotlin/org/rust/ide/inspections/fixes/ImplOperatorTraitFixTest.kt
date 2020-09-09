/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.fixes

import org.rust.ide.inspections.RsBinaryOpOrBinaryOpAssignInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class ImplOperatorTraitFixTest : RsInspectionsTestBase(RsBinaryOpOrBinaryOpAssignInspection::class) {

    fun `test fix impl operator trait`() = checkFixByText("Impl `Add` operation", """
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            <error descr="binary operation `+` cannot be applied to type `Test` [E0369]">a + /*caret*/b</error>;
        }
    """, """
        struct Test(i32);

        fn foo(a: Test, b: Test) {
            a + b;
        }

        impl Add/*caret*/ for Test {}
    """)
}
