/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsImplicitTraitObjectInspectionTest : RsInspectionsTestBase(RsImplicitTraitObjectInspection::class) {

    fun `test simple trait object`() = checkFixByText("Add 'dyn' keyword to trait object", """
        trait Trait {}
        fn main(){
            let a: &<warning descr="Trait objects without an explicit 'dyn' are deprecated">Trait<caret></warning>;
        }
    """, """
        trait Trait {}
        fn main(){
            let a: &dyn Trait;
        }
    """)

    fun `test with multiple bounds`() = checkFixByText("Add 'dyn' keyword to trait object", """
        trait Trait {}
        fn main(){
            let a: &<warning descr="Trait objects without an explicit 'dyn' are deprecated">Trait + Sync<caret></warning>;
        }
    """, """
        trait Trait {}
        fn main(){
            let a: &dyn Trait + Sync;
        }
    """)

    fun `test in function declaration`() = checkByText("""
       trait Trait {}
       fn test(a: Box<<warning>Trait</warning>>) -> Box<<warning>Trait</warning>> {}
    """)

    fun `test no warning on trait in bounds`() = checkByText("""
        trait Trait{}
        fn test<X>(a: X) where X : Trait {}
    """)

    fun `test no warning on Self`() = checkByText("""
        trait Y where Self: Foo {}
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2015)
    fun `test simple trait object in edition 2015`() = checkByText("""
        trait Trait {}
        fn main(){
            let a: &Trait;
        }
    """)
}
