/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

class RsTraitObjectInspectionTest : RsInspectionsTestBase(RsTraitObjectInspection::class) {

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test simple trait object`() = checkFixByText("Add dyn keyword to trait object", """
        trait Trait{}
        fn main(){
            let a:&<warning>Trait<caret></warning>;
        }
    """, """
        trait Trait{}
        fn main(){
            let a:&dyn Trait;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test with multiple bounds`() = checkFixByText("Add dyn keyword to trait object", """
        trait Trait{}
        fn main(){
            let a:&<warning>Trait + Sync<caret></warning>;
        }
    """, """
        trait Trait{}
        fn main(){
            let a:&dyn Trait + Sync;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test in function declaration`() = checkByText("""
       trait Trait{}
       fn test(a:Box<<warning>Trait</warning>>) -> Box<<warning>Trait</warning>> {}
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test no warning on trait in bounds`() = checkByText("""
        trait Trait{}
        fn test<X>(a:X) where X:Trait{}
    """)

}
