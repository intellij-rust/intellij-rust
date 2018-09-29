/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2015)
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibResolveTestEdition2015 : RsResolveTestBase() {

    fun `test try! macro`() = checkByCode("""
        struct S { field: u32 }
                    //X
        fn foo() -> Result<S, ()> { unimplemented!() }

        //noinspection RsTryMacro
        fn main() {
            let s = try!(foo());
            s.field;
            //^
        }
    """)

    fun `test resolve with no_std attribute 2`() = stubOnlyResolve("""
    //- main.rs
        #![no_std]

        extern crate alloc;

        use alloc::vec::Vec;

        fn foo(v: Vec) {}
                 //^ .../liballoc/vec.rs
    """)
}
