/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.MockEdition
import org.rust.UseNewResolve
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ignoreInNewResolve

class RsMacroResolveTest : RsResolveTestBase() {
    fun `test resolve simple matching with multiple pattern definition`() = checkByCode("""
        macro_rules! test {
            ($ test:expr) => (
               //X
                $ test
                 //^
            )
            ($ test:expr) => (
                $ test
            )
        }
    """)

    fun `test resolve simple matching with multiple matching`() = checkByCode("""
        macro_rules! test {
            ($ test:expr, $ ty:ty) => (
               //X
                $ test
                 //^
            )
        }
    """)

    fun `test resolve simple matching in complex matching`() = checkByCode("""
        macro_rules! test {
            ($ ($ test:expr),+, $ ty:ty) => (
              //X
                $ ($ test),+
                   //^
            )
        }
    """)

    fun `test resolve macro same scope`() = checkByCode("""
        macro_rules! foo_bar { () => () }
        //X
        foo_bar!();
        //^
    """)

    fun `test resolve macro in function`() = checkByCode("""
        macro_rules! foo_bar { () => () }
        //X
        fn main() {
            foo_bar!();
            //^
        }
    """)

    fun `test resolve macro mod lower`() = checkByCode("""
        macro_rules! foo_bar { () => () }
        //X
        mod b {
            fn main() {
                foo_bar!();
                //^
            }
        }
    """)

    fun `test resolve macro mod`() = checkByCode("""
        #[macro_use]
        mod a {
            macro_rules! foo_bar { () => () }
            //X
        }
        mod b {
            fn main() {
                foo_bar!();
                //^
            }
        }
    """)

    // Macros are scoped by lexical order
    // see https://github.com/intellij-rust/intellij-rust/issues/1474
    fun `test resolve macro mod wrong order`() = checkByCode("""
        mod b {
            fn main() {
                foo_bar!();
                //^ unresolved
            }
        }
        #[macro_use]
        mod a {
            macro_rules! foo_bar { () => () }
        }
    """)

    fun `test resolve macro in lexical order 1`() = checkByCode("""
        macro_rules! foo { () => () }
        #[macro_use]
        mod a {
            macro_rules! foo { () => () }
            #[macro_use]
            mod b {
                macro_rules! foo { () => () }
                macro_rules! foo { () => () }
            }  //X
        }
        fn main() {
            foo!();
        } //^
    """)

    fun `test resolve macro in lexical order 2`() = checkByCode("""
       #[macro_use]
        pub mod a {
            macro_rules! foo { () => () }
            macro_rules! foo { () => () }
            //X
            pub mod b {
                pub fn foo() {
                    foo!();
                    //^
                }
            }

        }
    """)

    fun `test resolve macro in lexical order 3`() = checkByCode("""
        macro_rules! foo { () => () }
        #[macro_use]
        mod a {
            macro_rules! bar { () => () }
            #[macro_use]
            mod b {
                macro_rules! foo { () => () }
                //X
                macro_rules! bar { () => () }
            }
        }
        fn main() {
            foo!();
        } //^
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test resolve macro in lexical order 4`() = checkByCode("""
        mod a {
            macro_rules! foo { () => {} }
                       //X
            fn main() {
                foo!();
            } //^
            macro_rules! foo { () => {} }
        }
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test resolve macro in lexical order 5`() = checkByCode("""
        mod a {
            macro_rules! foo { () => {} }
            macro_rules! foo { () => {} }
                       //X
            fn main() {
                foo!();
            } //^
        }
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test expand macro in lexical order 6`() = checkByCode("""
        mod a {
            struct Foo1;
                 //X
            macro_rules! foo { () => { use Foo1 as Foo2; } }
            foo!();
            macro_rules! foo { () => {} }
            fn main() {
                let x = Foo2;
            }         //^
        }
    """)

    @MockEdition(Edition.EDITION_2018)
    fun `test resolve macro in lexical order 7`() = checkByCode("""
        mod a {
            struct Foo1;
                 //X
            macro_rules! foo { () => {} }
            macro_rules! foo { () => { use Foo1 as Foo2; } }
            foo!();
            fn main() {
                let x = Foo2;
            }         //^
        }
    """)

    fun `test resolve macro missing macro_use`() = checkByCode("""
        // Missing #[macro_use] here
        mod a {
            macro_rules! foo_bar { () => () }
        }
        fn main() {
            foo_bar!();
            //^ unresolved
        }
    """, NameResolutionTestmarks.missingMacroUse.ignoreInNewResolve(project))

    fun `test macro_export macro is visible in the same crate without macro_use`() = checkByCode("""
        // #[macro_use] is not needed here
        mod a {
            #[macro_export]
            macro_rules! foo_bar { () => () }
        }                //X
        fn main() {
            foo_bar!();
            //^
        }
    """, NameResolutionTestmarks.processSelfCrateExportedMacros.ignoreInNewResolve(project))

    fun `test resolve macro missing macro_use mod`() = checkByCode("""
        // Missing #[macro_use] here
        mod a {
            macro_rules! foo_bar { () => () }
        }
        mod b {
            fn main() {
                foo_bar!();
                //^ unresolved
            }
        }
    """, NameResolutionTestmarks.missingMacroUse.ignoreInNewResolve(project))

    fun `test raw identifier 1`() = checkByCode("""
        macro_rules! r#match { () => () }
                     //X
        r#match!();
           //^
    """)

    fun `test raw identifier 2`() = checkByCode("""
        macro_rules! foo { () => () }
                    //X
        r#foo!();
         //^
    """)

    fun `test macro call with crate prefix 1`() = checkByCode("""
        #[macro_export]
        macro_rules! foo { () => () }
                    //X
        crate::foo!();
              //^
    """)

    fun `test macro call with crate prefix 2`() = checkByCode("""
        macro_rules! foo { () => () }

        crate::foo!();
              //^ unresolved
    """)

    // TODO
    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test legacy textual macro reexported as macro 2 in nested mod (reexport)`() = expect<IllegalStateException> {
        checkByCode("""
            mod inner {
                #[macro_export]
                macro_rules! foo_ {
                           //X
                    () => {}
                }
                pub use foo_ as foo;
                      //^
            }
        """)
    }

    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test legacy textual macro reexported as macro 2 in nested mod (import)`() = checkByCode("""
        mod inner {
            #[macro_export]
            macro_rules! foo_ {
                       //X
                () => {}
            }
            pub use foo_ as foo;
        }
        mod test {
            use crate::inner::foo;
                            //^
        }
    """)

    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test legacy textual macro reexported as macro 2 in nested mod (macro call)`() = checkByCode("""
        mod inner {
            #[macro_export]
            macro_rules! foo_ {
                       //X
                () => {}
            }
            pub use foo_ as foo;
        }

        inner::foo! {}
             //^
    """)

    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test legacy textual macro reexported as macro 2 in crate root (reexport)`() = checkByCode("""
        #[macro_export]
        macro_rules! foo_ {
                   //X
            () => {}
        }
        pub use foo_ as foo;
              //^
    """)

    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test legacy textual macro reexported as macro 2 in crate root (import)`() = checkByCode("""
        #[macro_export]
        macro_rules! foo_ {
                   //X
            () => {}
        }
        pub use foo_ as foo;
        mod test {
            use crate::foo;
                     //^
        }
    """)

    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test legacy textual macro reexported as macro 2 in crate root (macro call fqn)`() = checkByCode("""
        #[macro_export]
        macro_rules! foo_ {
                   //X
            () => {}
        }
        pub use foo_ as foo;

        crate::foo! {}
             //^
    """)

    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test legacy textual macro reexported as macro 2 in crate root (macro call)`() = checkByCode("""
        #[macro_export]
        macro_rules! foo_ {
                   //X
            () => {}
        }
        pub use foo_ as foo;

        foo! {}
        //^
    """)

    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test propagate expanded macro def`() = checkByCode("""
        mod outer {
            #[macro_use]
            mod inner {
                macro_rules! gen_foo {
                    () => {
                        macro_rules! foo { () => { use Bar1 as Bar2; } }
                    }
                }
                gen_foo!();
            }
            foo!();
            struct Bar1;
                 //X
            fn main() {
                Bar2;
            } //^
        }
    """)

    // From https://github.com/seed-rs/seed/blob/d9935ee25148c151931160d188d5f0e67c746cba/src/shortcuts.rs#L9-L45
    @UseNewResolve
    @MockEdition(Edition.EDITION_2018)
    fun `test generate two macro defs with same name`() = checkByCode("""
        mod outer {
            macro_rules! with_dollar_sign {
                ($($ body:tt)*) => {
                    macro_rules! __with_dollar_sign { $($ body)* }
                    __with_dollar_sign!($);
                }
            }

            macro_rules! gen {
                ($ name:ident) => {
                    // This replaces $ d with $ in the inner macro.
                    with_dollar_sign! {
                        ($ d:tt) => {
                            macro_rules! $ name {
                                ($ d i:item) => { $ d i };
                            }
                        }
                    }
                };
            }

            struct Foo1;
                 //X
            gen!(foo1);
            foo1!(use Foo1 as Foo2;);
            gen!(foo2);
            foo2!(use Foo2 as Foo3;);

            fn main() {
                Foo3;
            } //^
        }
    """)

    /** More macro tests in [RsPackageLibraryResolveTest] and [RsStubOnlyResolveTest] */
}
