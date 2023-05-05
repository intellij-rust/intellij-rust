/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.inspections.RsInspectionsTestBase

class RsBareTraitObjectsInspectionTest : RsInspectionsTestBase(RsBareTraitObjectsInspection::class) {

    @MockEdition(Edition.EDITION_2021)
    fun `test simple trait object in edition 2021`() = checkFixByText("Add 'dyn' keyword to trait object", """
        trait Trait {}
        fn main() {
            let a: &<error descr="Trait objects must include the `dyn` keyword [E0782]">Trait<caret></error>;
        }
    """, """
        trait Trait {}
        fn main() {
            let a: &dyn Trait;
        }
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test simple trait object in edition 2018`() = checkFixByText("Add 'dyn' keyword to trait object", """
        trait Trait {}
        fn main() {
            let a: &<warning descr="Trait objects must include the `dyn` keyword [E0782]This is accepted in the current edition (Rust 2018) but is a hard error in Rust 2021!">Trait<caret></warning>;
        }
    """, """
        trait Trait {}
        fn main() {
            let a: &dyn Trait;
        }
    """)

    fun `test with multiple bounds`() = checkFixByText("Add 'dyn' keyword to trait object", """
        trait Trait {}
        fn main() {
            let a: &<error descr="Trait objects must include the `dyn` keyword [E0782]">Trait + Sync<caret></error>;
        }
    """, """
        trait Trait {}
        fn main() {
            let a: &dyn Trait + Sync;
        }
    """)

    fun `test in function declaration`() = checkByText("""
       trait Trait {}
       fn test(a: Box<<error>Trait</error>>) -> Box<<error>Trait</error>> {}
    """)

    fun `test no error on trait in bounds`() = checkByText("""
        trait Trait{}
        fn test<X>(a: X) where X : Trait {}
    """)

    fun `test no error on Self`() = checkByText("""
        trait Y where Self: Foo {}
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test allow bare_trait_objects`() = checkWarnings("""
        trait Trait {}

        #[allow(bare_trait_objects)]
        fn main() {
            let a: Trait;
        }
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test allow rust_2018_idioms`() = checkWarnings("""
        trait Trait {}

        #[allow(rust_2018_idioms)]
        fn main() {
            let a: Trait;
        }
    """)

    @MockEdition(Edition.EDITION_2015)
    fun `test simple trait object in edition 2015`() = checkByText("""
        trait Trait {}
        fn main() {
            let a: &Trait;
        }
    """)
}
