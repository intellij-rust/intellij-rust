/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace.Edition

@MockEdition(Edition.EDITION_2018)
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
        }         //^ ...vec/mod.rs
    """)

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
