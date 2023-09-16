/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.borrowck

import org.rust.ProjectDescriptor
import org.rust.WithExperimentalFeatures
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.experiments.RsExperiments.MIR_BORROW_CHECK
import org.rust.ide.inspections.RsBorrowCheckerInspection
import org.rust.ide.inspections.RsInspectionsTestBase

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
@WithExperimentalFeatures(MIR_BORROW_CHECK)
class RsBorrowCheckerBorrowsTest : RsInspectionsTestBase(RsBorrowCheckerInspection::class) {

    fun `test E0505 1`() = checkByText( """
        struct S;
        fn main() {
            let a = S;
            let b = &a;
            let c = <error descr="A value was moved out while it was still borrowed [E0505]">a</error>;
            let d = b;
        }
    """, checkWarn = false)

    fun `test E0505 2`() = checkByText("""
        struct S;
        fn main() {
            let a = S;
            let b = &a;
            let c = <error descr="A value was moved out while it was still borrowed [E0505]">a</error>;
        }
    """, checkWarn = false)

    fun `test E0505 3`() = checkByText("""
        struct S;
        fn main() {
            let a = S;
            let b = S;
            let ar = if false {
                &a
            } else {
                &b
            };
            let c = <error descr="A value was moved out while it was still borrowed [E0505]">a</error>;
            let d = ar;
        }
    """, checkWarn = false)

    fun `test E0505 with a borrow in a function argument`() = checkByText( """
        struct S;
        fn main() {
            let a = S;
            let b = foo(&a);
            let c = /*error descr="A value was moved out while it was still borrowed [E0505]"*/a/*error**/;
            let d = b;
        }
        fn foo<T>(a: T) -> T {}
    """, checkWarn = false)
}
