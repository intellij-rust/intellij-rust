/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition.EDITION_2018
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.macros.MacroExpansionScope

@MinRustcVersion("1.46.0")
@MockEdition(EDITION_2018)
@ExpandMacros(MacroExpansionScope.WORKSPACE)
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
class RsProcMacroExpansionResolveTest : RsResolveTestBase() {
    fun `test simple function-like macro`() = checkByCode("""
        use test_proc_macros::function_like_as_is;

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        function_like_as_is! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^
    """)

    fun `test custom derive`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[derive(DeriveImplForFoo)] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    fun `test custom derive dollar crate`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        macro_rules! foo {
            () => {
                #[derive($ crate::DeriveImplForFoo)]
                struct Foo;
            };
        }
        foo!();
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test custom derive in enabled cfg_attr attribute`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[cfg_attr(intellij_rust, derive(DeriveImplForFoo))] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }    //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test custom derive in disabled cfg_attr attribute`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[cfg_attr(not(intellij_rust), derive(DeriveImplForFoo))] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }

        fn main() {
            Foo.foo().bar()
        }           //^ unresolved
    """)

    fun `test not expanded if not a custom derive macro is used in custom derive position`() = checkByCode("""
        use test_proc_macros::function_like_generates_impl_for_foo;

        #[derive(function_like_generates_impl_for_foo)] // Not a custom derive
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }

        fn main() {
            Foo.foo().bar()
        }           //^ unresolved
    """)

    fun `test custom derive expands to a struct`() = checkByCode("""
        use test_proc_macros::DeriveStructFooDeclaration;

        #[derive(DeriveStructFooDeclaration)] // struct Foo;
        struct Bar;

        impl Foo {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.bar()
        }     //^
    """)

    fun `test custom derive expands to macro declaration`() = checkByCode("""
        use test_proc_macros::DeriveMacroFooThatExpandsToStructFoo;

        #[derive(DeriveMacroFooThatExpandsToStructFoo)] // macro_rules! foo { () => { struct Foo; } }
        struct Bar;
        foo!();

        impl Foo {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.bar()
        }     //^
    """)

    fun `test custom derive expands to a macro declaration, the macro is unresolved inside the struct`() = checkByCode("""
        use test_proc_macros::DeriveMacroFooThatExpandsToStructFoo;

        #[derive(DeriveMacroFooThatExpandsToStructFoo)] // macro_rules! foo { () => { struct Foo; } }
        struct Bar {
            f: foo!()
        }    //^ unresolved
    """)

    fun `test custom derive expands to a macro call`() = checkByCode("""
        use test_proc_macros::DeriveMacroFooInvocation;

        macro_rules! foo { () => { struct Foo; } }
        #[derive(DeriveMacroFooInvocation)] // foo!()
        struct Bar;

        impl Foo {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.bar()
        }     //^
    """)

    fun `test 2 custom derive expands to a macro declaration and a macro call`() = checkByCode("""
        use test_proc_macros::*;

        #[derive(DeriveMacroFooThatExpandsToStructFoo)] // macro_rules! foo { () => { struct Foo; } }
        #[derive(DeriveMacroFooInvocation)] // foo!()
        struct Bar;

        impl Foo {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.bar()
        }     //^
    """)

    fun `test 2 custom derive expands to a macro declaration and a macro call 1`() = checkByCode("""
        use test_proc_macros::*;

        macro_rules! bar {
            () => {
                #[derive(DeriveMacroFooThatExpandsToStructFoo)] // macro_rules! foo { () => { struct Foo; } }
                #[derive(DeriveMacroFooInvocation)] // foo!()
                struct Bar;
            };
        }
        bar!();

        impl Foo {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.bar()
        }     //^
    """)

    fun `test 2 custom derive expands to a macro declaration and a macro call 2`() = checkByCode("""
        use test_proc_macros::*;

        #[derive(DeriveMacroFooThatExpandsToStructFoo, DeriveMacroFooInvocation)]
        // macro_rules! foo { () => { struct Foo; } }
        // foo!()
        struct Bar;

        impl Foo {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.bar()
        }     //^
    """)

    fun `test 2 custom derive expands to a macro declaration and a macro call 3`() = checkByCode("""
        use test_proc_macros::*;

        macro_rules! foo { () => { struct Bar; } }

        #[derive(DeriveMacroFooInvocation)] // foo!()
        #[derive(DeriveMacroFooThatExpandsToStructFoo)] // macro_rules! foo { () => { struct Foo; } }
        struct Baz;

        impl Bar {
            fn bar(&self) {}
        }     //X

        fn main() {
            Bar.bar()
        }     //^
    """)

    fun `test 2 custom derive expands to a macro declaration and a macro call 4`() = checkByCode("""
        use test_proc_macros::*;

        macro_rules! bar { () => { struct Bar; } }

        #[derive(DeriveMacroBarInvocation)] // bar!()
        #[derive(DeriveMacroFooThatExpandsToStructFoo)] // macro_rules! foo { () => { struct Foo; } }
        struct Baz;

        foo!(); // struct Foo;

        impl Foo {
            fn bar(&self) -> Bar { Bar }
        }

        impl Bar {
            fn baz(&self) {}
        }     //X

        fn main() {
            Foo.bar().baz()
        }           //^
    """)

    fun `test attr legacy macro`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        struct S;

        macro_rules! foo {
            () => {};//X
        }

        foo!{}
        //^
    """)

    fun `test attr impl`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        struct S;

        #[attr_as_is]
        impl S {
            fn foo(&self) {}
        }    //X

        fn main() {
            S.foo();
        }   //^
    """)

    fun `test attr mod`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        mod m {
            pub fn foo() {}
        }        //X

        fn main() {
            m::foo();
        }    //^
    """)

    fun `test attr fn`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        fn foo() {}
           //X
        fn main() {
            foo();
        } //^
    """)

    fun `test attr fn under 2 macros`() = checkByCode("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        #[attr_as_is]
        fn foo() {}
           //X
        fn main() {
            foo();
        } //^
    """)

    fun `test hardcoded not a macro fn`() = checkByCode("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        fn foo() {}
           //X
        fn main() {
            foo();
        } //^
    """)

    fun `test hardcoded not a macro impl`() = checkByCode("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        struct S;

        #[attr_hardcoded_not_a_macro]
        impl S {
            fn foo() {}
               //X
        }
        fn main() {
            S::foo();
        }    //^
    """)

    fun `test attr legacy macro 2`() = checkByCode("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        fn foo() {}

        macro_rules! foo {
            () => {};//X
        }

        foo!{}
        //^
    """)

    fun `test attr replaced item is unresolved`() = checkByCode("""
        use test_proc_macros::attr_replace_with_attr;

        #[attr_replace_with_attr(struct X{})]
        fn foo() {}
        fn main() {
            foo();
        } //^ unresolved
    """)

    fun `test attr expanded to attribute argument`() = checkByCode("""
        use test_proc_macros::attr_replace_with_attr;

        #[attr_replace_with_attr(struct X{})]
        foo! {}                       //X
        fn main() {
            let _: X;
        }        //^
    """)

    fun `test attr expanded from a function-like macro`() = checkByCode("""
        use test_proc_macros::attr_as_is;
        macro_rules! as_is {
            ($ i:item) => { $ i };
        }
        as_is! {
            #[attr_as_is]
            fn foo() {}
             //X
        }
        fn main() {
            foo();
        } //^
    """)

    fun `test function-like macro expanded from attr macro`() = checkByCode("""
        use test_proc_macros::attr_replace_with_attr;
        macro_rules! as_is {
            ($ i:item) => { $ i };
        }
        #[attr_replace_with_attr(as_is! { struct X{} })]
        foo! {}                                //X
        fn main() {
            let _: X;
        }        //^
    """)

    fun `test attr qualified by $crate`() = stubOnlyResolve("""
    //- lib.rs
        pub mod foo {
            pub use test_proc_macros::attr_as_is as attr_as_is_renamed;
        }
        #[macro_export]
        macro_rules! with_proc_macro {
            ($ i:item) => {
                #[$ crate::foo::attr_as_is_renamed]
                $ i
            };
        }
    //- main.rs
        use test_package::with_proc_macro;
        with_proc_macro! {
            fn foo() {}
        }
        fn main() {
            foo();
        } //^ main.rs
    """)

    fun `test attr qualified by $crate 2`() = stubOnlyResolve("""
    //- lib.rs
        pub mod foo {
            pub use test_proc_macros::attr_as_is as attr_as_is_renamed;
        }
        #[macro_export]
        macro_rules! with_proc_macro {
            ($ i:item) => {
                #[test_proc_macros::attr_as_is]
                #[$ crate::foo::attr_as_is_renamed]
                $ i
            };
        }
    //- main.rs
        use test_package::with_proc_macro;
        with_proc_macro! {
            fn foo() {}
        }
        fn main() {
            foo();
        } //^ main.rs
    """)

    override val followMacroExpansions: Boolean
        get() = true
}
