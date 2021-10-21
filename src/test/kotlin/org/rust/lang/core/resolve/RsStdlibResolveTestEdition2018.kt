/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibResolveTestEdition2018 : RsResolveTestBase() {
    fun `test extern crate std is not injected on 2018 edition`() = stubOnlyResolve("""
    //- main.rs
        use crate::std::mem;
                 //^ unresolved
    """)

    // BACKCOMPAT: Rust 1.50. Vec struct was moved into `vec/mod.rs` since Rust 1.51
    fun `test extra use of prelude item`() = stubOnlyResolve("""
    //- main.rs
        use Vec;

        fn main() {
            let a = Vec::<i32>::new();
        }         //^ .../vec.rs|...vec/mod.rs
    """, ItemResolutionTestmarks.extraAtomUse.ignoreInNewResolve(project))

    fun `test resolve core crate without extern crate`() = stubOnlyResolve("""
    //- main.rs
        use core::cell::Cell;
          //^ ...core/src/lib.rs|...core/lib.rs
    """)

    fun `test alloc crate unresolved without extern crate`() = stubOnlyResolve("""
    //- main.rs
        use alloc::rc::Rc;
          //^ unresolved
    """)
}
