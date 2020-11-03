/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.IgnoreInNewResolve
import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace

@IgnoreInNewResolve
@MockEdition(CargoWorkspace.Edition.EDITION_2018)
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

    // FIXME
    fun `test resolve attr proc macro from macro call with full path`() = expect<IllegalStateException> {
        stubOnlyResolve("""
        //- dep-proc-macro/lib.rs
            #[proc_macro_attribute]
            pub fn example_proc_macro(attr: TokenStream, item: TokenStream) -> TokenStream { item }
        //- lib.rs
            use dep_proc_macro
            #[dep_proc_macro::example_proc_macro]
                                //^ dep-proc-macro/lib.rs
            struct S;
    """)
    }

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

    fun `test resolve custom derive proc macro from derive attribute with full path`() = stubOnlyResolve("""
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
}
