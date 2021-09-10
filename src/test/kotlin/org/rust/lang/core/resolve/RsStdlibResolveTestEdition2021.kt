/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.MinRustcVersion
import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace

@MinRustcVersion("1.56.0-nightly")
@MockEdition(CargoWorkspace.Edition.EDITION_2021)
@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsStdlibResolveTestEdition2021 : RsResolveTestBase() {
    fun `test 2021 edition prelude`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            char::try_from(0u32);
        }       //^ .../convert.rs
    """)
}
