/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
@ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
class RsProcMacroResolveTest : RsResolveTestBase() {
    // FIXME
    fun `test resolve bang proc definition in macro crate itself`() = expect<IllegalStateException> {
        stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro]
            pub fn example_proc_macro_1(item: TokenStream) -> TokenStream { item }

            #[proc_macro]
            pub fn example_proc_macro_2(item: TokenStream) -> TokenStream { example_proc_macro_1(item) }
                                                                            //^ dep-proc-macro/lib.rs
        """)
    }

    // FIXME
    fun `test resolve bang proc definition in macro in macro crate itself from mod`() = expect<IllegalStateException> {
        stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro]
            pub fn example_proc_macro_1(item: TokenStream) -> TokenStream { item }

            mod foo {
                use super::example_proc_macro_1;
                pub fn proc_macro_user(item: TokenStream) -> TokenStream { example_proc_macro_1(item) }
            }                                                                //^ dep-proc-macro/lib.rs
        """)
    }

    fun `test resolve use item inside proc macro crate`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        mod foo { pub fn bar() {} }
        use foo::bar;
                //^ dep-proc-macro/lib.rs

        #[proc_macro]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
    """)

    fun `test resolve bang proc macro from use item`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
    //- lib.rs
        use dep_proc_macro::example_proc_macro;
                            //^ dep-proc-macro/lib.rs
    """)

    fun `test proc macro crate cannot export anything but proc macros`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        pub fn unseen_function() {}
    //- lib.rs
        use dep_proc_macro::unseen_function;
                                //^ unresolved
    """)

    fun `test resolve bang proc macro from macro call`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
    //- lib.rs
        use dep_proc_macro::example_proc_macro;
        example_proc_macro!();
        //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve attr proc macro in use item`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
    //- lib.rs
        use dep_proc_macro::example_proc_macro;
                          //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve attr proc macro from macro call`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
    //- lib.rs
        use dep_proc_macro::example_proc_macro;

        #[example_proc_macro]
              //^ dep-proc-macro/lib.rs
        struct S;
    """)

    fun `test resolve attr proc macro from macro call under cfg_attr`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
    //- lib.rs
        use dep_proc_macro::example_proc_macro;

        #[cfg_attr(unix, example_proc_macro)]
                        //^ dep-proc-macro/lib.rs
        struct S;
    """)

    fun `test resolve attr proc macro from macro call with full path`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_attribute]
            pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[dep_proc_macro::example_proc_macro]
                                //^ dep-proc-macro/lib.rs
            struct S;
    """)

    fun `test attr proc macro is not resolved to a declarative macro`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_attribute]
            pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
        //- lib.rs
            use dep_proc_macro::example_proc_macro;

            macro_rules! example_proc_macro {
                () => {};
            }

            #[example_proc_macro]
               //^ dep-proc-macro/lib.rs
            struct S;
    """)

    fun `test resolve custom derive proc macro in use item`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_derive(ProcMacroName)]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
    //- lib.rs
        use dep_proc_macro::ProcMacroName;
                          //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve custom derive proc macro from derive attribute`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_derive(ProcMacroName)]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
    //- lib.rs
        use dep_proc_macro::ProcMacroName;
        #[derive(ProcMacroName)]
                  //^ dep-proc-macro/lib.rs
        struct S;
    """)

    fun `test resolve custom derive proc macro from derive attribute with full path 1`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_derive(ProcMacroName)]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
    //- lib.rs
        #[derive(dep_proc_macro::ProcMacroName)]
                  //^ dep-proc-macro/lib.rs
        struct S;
    """)

    fun `test resolve custom derive proc macro from derive attribute with full path 2`() = stubOnlyResolve("""
    //- dep-proc-macro/lib.rs
        #[proc_macro_derive(ProcMacroName)]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
    //- lib.rs
        #[derive(dep_proc_macro::ProcMacroName)]
                                //^ dep-proc-macro/lib.rs
        struct S;
    """)

    fun `test resolve bang proc macro reexported from lib to main from use item`() = stubOnlyResolve("""
    //- lib.rs
        extern crate dep_proc_macro;
        pub use dep_proc_macro::example_proc_macro;

    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }

    //- main.rs
        use test_package::example_proc_macro;
                            //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve bang proc macro reexported from lib to main from macro call`() = stubOnlyResolve("""
    //- lib.rs
        extern crate dep_proc_macro;
        pub use dep_proc_macro::example_proc_macro;

    //- dep-proc-macro/lib.rs
        #[proc_macro]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }

    //- main.rs
        use test_package::example_proc_macro;

        example_proc_macro!();
                    //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve custom derive proc macro reexported from lib to main from use item`() = stubOnlyResolve("""
    //- lib.rs
        extern crate dep_proc_macro;
        pub use dep_proc_macro::ProcMacroName;

    //- dep-proc-macro/lib.rs
        #[proc_macro_derive(ProcMacroName)]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }

    //- main.rs
        use test_package::ProcMacroName;
                         //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve custom derive proc macro reexported from lib to main from macro call`() = stubOnlyResolve("""
    //- lib.rs
        extern crate dep_proc_macro;
        pub use dep_proc_macro::ProcMacroName;

    //- dep-proc-macro/lib.rs
        #[proc_macro_derive(ProcMacroName)]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }

    //- main.rs
        use test_package::ProcMacroName;

        #[derive(ProcMacroName)]
                 //^ dep-proc-macro/lib.rs
        struct S;
    """)

    fun `test resolve attribute proc macro reexported from lib to main from use item`() = stubOnlyResolve("""
    //- lib.rs
        extern crate dep_proc_macro;
        pub use dep_proc_macro::example_proc_macro;

    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }

    //- main.rs
        use test_package::example_proc_macro;
                             //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve attribute proc macro reexported from lib to main from macro call`() = stubOnlyResolve("""
    //- lib.rs
        extern crate dep_proc_macro;
        pub use dep_proc_macro::example_proc_macro;

    //- dep-proc-macro/lib.rs
        #[proc_macro_attribute]
        pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }

    //- main.rs
        use test_package::example_proc_macro;

        #[example_proc_macro]
                 //^ dep-proc-macro/lib.rs
        struct S;
    """)

    fun `test resolve bang proc macro from macro call through macro_use`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            example_proc_macro!();
              //^ dep-proc-macro/lib.rs
    """)

    @UseNewResolve
    fun `test resolve bang proc macro from macro call through macro_use with rename`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            use example_proc_macro as example_proc_macro_alias;

            example_proc_macro_alias!();
              //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve bang proc macro through macro_use to the last extern crate 1`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- dep-proc-macro-2/lib.rs
            #[proc_macro]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;
            #[macro_use]
            extern crate dep_proc_macro_2;

            example_proc_macro!();
              //^ dep-proc-macro-2/lib.rs
    """)

    fun `test resolve bang proc macro through macro_use to the last extern crate 2`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- dep-proc-macro-2/lib.rs
            #[proc_macro]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro_2;
            #[macro_use]
            extern crate dep_proc_macro;

            example_proc_macro!();
              //^ dep-proc-macro/lib.rs
    """)

    fun `test resolve bang proc macro through macro_use to the last extern crate 3`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro]
            pub fn example_macro(item: TokenStream) -> TokenStream { item }
        //- dep-lib/lib.rs
            #[macro_export]
            macro_rules! example_macro { () => {}; }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;
            #[macro_use]
            extern crate dep_lib_target;

            example_macro!();
              //^ dep-lib/lib.rs
    """)

    // TODO fix name resolution order
    fun `test resolve bang proc macro through macro_use to the last extern crate 4`() = expect<IllegalStateException> {
    stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro]
            pub fn example_macro(item: TokenStream) -> TokenStream { item }
        //- dep-lib/lib.rs
            #[macro_export]
            macro_rules! example_macro { () => {}; }
        //- lib.rs
            #[macro_use]
            extern crate dep_lib_target;
            #[macro_use]
            extern crate dep_proc_macro;

            example_macro!();
              //^ dep-proc-macro/lib.rs
    """)
    }

    fun `test resolve attr proc macro from macro call through macro_use`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_attribute]
            pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            #[example_proc_macro]
              //^ dep-proc-macro/lib.rs
            struct S;
    """)

    fun `test attr proc macro is not resolved to decl macro through macro_use 1`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_attribute]
            pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            macro_rules! example_proc_macro { () => {}; }

            #[example_proc_macro]
              //^ dep-proc-macro/lib.rs
            struct S;
    """)

    fun `test attr proc macro is not resolved to decl macro through macro_use 2`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_attribute]
            pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            #[macro_use]
            mod foo {
                macro_rules! example_proc_macro { () => {}; }
            }

            #[example_proc_macro]
              //^ dep-proc-macro/lib.rs
            struct S;
    """)

    @UseNewResolve
    fun `test resolve attr proc macro from macro call through macro_use with rename`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_attribute]
            pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            use example_proc_macro as example_proc_macro_alias;

            #[example_proc_macro_alias]
              //^ dep-proc-macro/lib.rs
            struct S;
    """)

    @UseNewResolve
    fun `test resolve custom derive proc macro from macro call through macro_use`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            #[derive(ProcMacroName)]
                    //^ dep-proc-macro/lib.rs
            struct S;
    """)

    @UseNewResolve
    fun `test custom derive proc macro is not resolved to decl macro through macro_use 1`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            #[allow(non_snake_case)]
            macro_rules! ProcMacroName { () => {}; }

            #[derive(ProcMacroName)]
                    //^ dep-proc-macro/lib.rs
            struct S;
    """)

    @UseNewResolve
    fun `test custom derive proc macro is not resolved to decl macro through macro_use 2`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            #[macro_use]
            mod foo {
                #[allow(non_snake_case)]
                macro_rules! ProcMacroName { () => {}; }
            }

            #[derive(ProcMacroName)]
                    //^ dep-proc-macro/lib.rs
            struct S;
    """)

    @UseNewResolve
    fun `test resolve custom derive proc macro from macro call through macro_use with rename`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;

            use ProcMacroName as ProcMacroNameAlias;

            #[derive(ProcMacroNameAlias)]
                    //^ dep-proc-macro/lib.rs
            struct S;
    """)

    @UseNewResolve
    fun `test resolve custom derive through macro_use to the last extern crate 1`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- dep-proc-macro-2/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro;
            #[macro_use]
            extern crate dep_proc_macro_2;

            #[derive(ProcMacroName)]
                    //^ dep-proc-macro-2/lib.rs
            struct S;
    """)

    @UseNewResolve
    fun `test resolve custom derive through macro_use to the last extern crate 2`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- dep-proc-macro-2/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[macro_use]
            extern crate dep_proc_macro_2;
            #[macro_use]
            extern crate dep_proc_macro;

            #[derive(ProcMacroName)]
                    //^ dep-proc-macro/lib.rs
            struct S;
    """)

    @UseNewResolve
    fun `test resolve custom derive through macro_use to the last extern crate 3`() = stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- dep-proc-macro-2/lib.rs
            #[proc_macro_derive(ProcMacroName)]
            pub fn example_proc_macro(item: TokenStream) -> TokenStream { item }
        //- lib.rs
            #[derive(ProcMacroName)]
                    //^ dep-proc-macro/lib.rs
            struct S;

            #[macro_use]
            extern crate dep_proc_macro_2;
            #[macro_use]
            extern crate dep_proc_macro;
    """)
}
