/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ignoreInNewResolve

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibResolveTestEdition2018 : RsResolveTestBase() {
    fun `test extern crate std is not injected on 2018 edition`() = stubOnlyResolve("""
    //- main.rs
        use crate::std::mem;
                 //^ unresolved
    """)

    fun `test extra use of prelude item`() = stubOnlyResolve("""
    //- main.rs
        use Vec;

        fn main() {
            let a = Vec::<i32>::new();
        }         //^ .../vec.rs
    """, ItemResolutionTestmarks.extraAtomUse.ignoreInNewResolve())
}
