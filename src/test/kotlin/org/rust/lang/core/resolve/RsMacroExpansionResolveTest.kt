/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.stdext.BothEditions

@ExpandMacros
class RsMacroExpansionResolveTest : RsResolveTestBase() {
    override val followMacroExpansions: Boolean get() = true

    fun `test expand item`() = checkByCode("""
        macro_rules! if_std {
            ($ i:item) => (
                $ i
            )
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^
    """)

    fun `test expand items star`() = checkByCode("""
        macro_rules! if_std {
            ($ ($ i:item)*) => ($ (
                $ i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^

    """)

    fun `test expand items star with reexport`() = checkByCode("""
        macro_rules! if_std {
            ($ ($ i:item)*) => ($ (
                $ i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            use Foo as Bar;
        }

        fn main() {
            Bar.bar()
        }      //^
    """)

    fun `test expand items star with reexport from expansion`() = checkByCode("""
        macro_rules! if_std {
            ($ ($ i:item)*) => ($ (
                $ i
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        if_std! {
            fn foo() -> Foo { Foo }
            use foo as bar;
        }

        fn main() {
            bar().bar()
        }        //^
    """)

    fun `test expand items star with nested macro calls`() = checkByCode("""
        macro_rules! if_std {
            ($ ($ i:item)*) => ($ (
                $ i
            )*)
        }

        macro_rules! foo {
            ($ ($ i:item)*) => ($ (
                if_std! { $ i }
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        foo! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^
    """)

    fun `test expand items star with infinite recursive nested macro calls`() = checkByCode("""
        macro_rules! foo {
            ($ ($ i:item)*) => ($ (
                foo! { $ i }
            )*)
        }

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }

        foo! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^ unresolved
    """)

    fun `test method defined with a macro`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { fn $ i(&self) -> $ j { unimplemented!() } }
        }

        struct Foo;
        struct Bar;
        impl Foo { foo!(foo, Bar); }
        impl Bar { fn bar(&self) {} }
                    //X
        fn main() {
            Foo.foo().bar();
        }           //^
    """)

    fun `test method defined with a stubbed macro`() = stubOnlyResolve("""
    //- foo.rs
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { pub fn $ i(&self) -> $ j { unimplemented!() } }
        }
        pub struct Foo;
        impl Foo { foo!(foo, super::Bar); }
    //- main.rs
        mod foo;
        use foo::Foo;

        struct Bar;
        impl Bar { fn bar(&self) {} }
        fn main() {
            Foo.foo().bar();
        }           //^ main.rs
    """)

    // This is a test for RsAbstractable.owner when RsAbstractable is expanded from a macro
    fun `test generic trait method defined with a stubbed macro`() = stubOnlyResolve("""
    //- foo.rs
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { pub fn $ i(&self) -> $ j { unimplemented!() } }
        }
        pub struct Foo<T>(T);
        trait Trait<T> { foo!(foo, T); }
        impl<T> Trait<T> for Foo<T> {}
    //- main.rs
        mod foo;
        use foo::Foo;

        struct Bar;
        impl Bar { fn bar(&self) {} }
        fn main() {
            Foo(Bar).foo().bar();
        }                //^ main.rs
    """)

    fun `test trait method defined with a macro`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { fn $ i(&self) -> $ j { unimplemented!() } }
        }

        trait FooTrait { foo!(foo, Bar); }
        struct Foo;
        struct Bar;
        impl FooTrait for Foo {}
        impl Bar { fn bar(&self) {} }
                    //X
        fn main() {
            Foo.foo().bar();
        }           //^
    """)

    fun `test method defined with a nested macro call`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { fn $ i(&self) -> $ j { unimplemented!() } }
        }

        macro_rules! bar {
            ($ i:ident, $ j:ty) => { foo!($ i, $ j); }
        }

        struct Foo;
        struct Bar;
        impl Foo { bar!(foo, Bar); }
        impl Bar { fn bar(&self) {} }
                    //X
        fn main() {
            Foo.foo().bar();
        }           //^
    """)

    fun `test expand impl members with infinite recursive nested macro calls`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { foo!($ i, $ j) }
        }

        struct Foo;
        struct Bar;
        impl Foo { bar!(foo, Bar); }
        impl Bar { fn bar(&self) {} }
        fn main() {
            Foo.foo().bar();
        }           //^ unresolved
    """)

    fun `test 'crate' metavar in same crate`() = checkByCode("""
        struct Foo;
        impl Foo {
            pub fn bar(&self) {}
        }     //X

        macro_rules! foo {
            () => { fn foo() -> $ crate::Foo { unimplemented!() } }
        }
        foo!();

        fn main() {
            foo().bar()
        }       //^
    """)

    // We need to test macros expanded to import,
    // because paths in imports are resolved using different code when Resolve2 is enabled.
    fun `test 'crate' metavar in same crate (macro expanded to import)`() = checkByCode("""
        fn func() {}
         //X

        macro_rules! foo {
            () => { use $ crate::func; }
        }

        mod inner {
            foo!();
            fn main() {
                func();
            } //^
        }
    """)

    fun `test 'crate' metavar in same crate 1 (macro expanded to inline mod)`() = checkByCode("""
        fn func() {}
         //X

        macro_rules! foo {
            () => {
                mod inner { pub use $ crate::func; }
            }
        }

        foo!();
        fn main() {
            inner::func();
        }        //^
    """)

    fun `test 'crate' metavar in same crate 2 (macro expanded to inline mod)`() = checkByCode("""
        fn func() {}
         //X

        macro_rules! foo1 {
            () => {
                mod inner { foo2!($ crate::func); }
            }
        }
        macro_rules! foo2 {
            ($ path:path) => { pub use $ path; }
        }

        foo1!();
        fn main() {
            inner::func();
        }        //^
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
                 //X
        }
        #[macro_export]
        macro_rules! foo {
            () => { fn foo() -> $ crate::Foo { unimplemented!() } }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package;

        foo!();

        fn main() {
            foo().bar()
        }       //^ lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar (macro expanded to import)`() = stubOnlyResolve("""
    //- lib.rs
        pub fn func() {}
             //X
        #[macro_export]
        macro_rules! foo {
            () => { use $ crate::func; }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package;

        foo!();

        fn main() {
            func();
        } //^ lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar (dollar crate in path of macro call)`() = stubOnlyResolve("""
    //- lib.rs
        pub fn func() {}
             //X
        #[macro_export]
        macro_rules! foo0 {
            () => { use $ crate::func; }
        }
        #[macro_export]
        macro_rules! foo {
            () => { $ crate::foo0!(); }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package;

        foo!();

        fn main() {
            func();
        } //^ lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar with alias`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
                 //X
        }
        #[macro_export]
        macro_rules! foo {
            () => { fn foo() -> $ crate::Foo { unimplemented!() } }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package as package;

        foo!();

        fn main() {
            foo().bar()
        }       //^ lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar with alias (macro expanded to import)`() = stubOnlyResolve("""
    //- lib.rs
        pub fn func() {}
             //X
        #[macro_export]
        macro_rules! foo {
            () => { use $ crate::func; }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package as package;

        foo!();

        fn main() {
            func();
        } //^ lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar with alias (macro expanded to import with group)`() = stubOnlyResolve("""
    //- lib.rs
        pub fn func() {}
             //X
        #[macro_export]
        macro_rules! foo {
            () => { use $ crate::{func}; }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package as package;

        foo!();

        fn main() {
            func();
        } //^ lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar with macro call not in crate root`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
                 //X
        }
        #[macro_export]
        macro_rules! foo {
            () => { fn foo() -> $ crate::Foo { unimplemented!() } }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package as package;

        mod a {
            foo!();

            fn main() {
                foo().bar()
            }       //^ lib.rs
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar with macro call not in crate root (macro expanded to import)`() = stubOnlyResolve("""
    //- lib.rs
        pub fn func() {}
             //X
        #[macro_export]
        macro_rules! foo {
            () => { use $ crate::func; }
        }
    //- main.rs
        #[macro_use]
        extern crate test_package as package;

        mod a {
            foo!();

            fn main() {
                func()
            } //^ lib.rs
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar passed to another macro in a different crate`() = stubOnlyResolve("""
    //- trans-lib/lib.rs
        #[macro_export]
        macro_rules! def_fn {
            ($ ret_ty:ty) => { fn foo() -> $ ret_ty { unimplemented!() } }
        }
    //- dep-lib/lib.rs
        #[macro_use]
        extern crate trans_lib;
        pub use trans_lib::def_fn;
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
                 //X
        }
        #[macro_export]
        macro_rules! foo {
            () => { def_fn! { $ crate::Foo } }
        }
    //- main.rs
        #[macro_use]
        extern crate dep_lib_target;

        foo!(); // def_fn! { IntellijRustDollarCrate_dep_lib::Foo }

        fn main() {
            foo().bar()
        }       //^ dep-lib/lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar passed to another macro in a different crate 2`() = stubOnlyResolve("""
    //- trans-lib-2/lib.rs
        #[macro_export]
        macro_rules! def_fn_2 {
            ($ ret_ty:ty) => { $ crate::def_fn_3! { $ ret_ty  } }
        }
        #[macro_export]
        macro_rules! def_fn_3 {
            ($ ret_ty:ty) => {
                use $ crate::def_fn_4;
                def_fn_4! { $ ret_ty  }
            }
        }
        #[macro_export]
        macro_rules! def_fn_4 {
            ($ ret_ty:ty) => { fn foo() -> $ ret_ty { unimplemented!() } }
        }
    //- trans-lib/lib.rs
        #[macro_use]
        extern crate trans_lib_2;
        pub use trans_lib_2::def_fn_2;

        #[macro_export]
        macro_rules! def_fn {
            ($ ret_ty:ty) => { def_fn_2! { $ ret_ty  } }
        }
    //- dep-lib/lib.rs
        #[macro_use]
        extern crate trans_lib;
        pub use trans_lib::{def_fn, def_fn_2};

        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
                 //X
        }
        #[macro_export]
        macro_rules! foo {
            () => { def_fn! { $ crate::Foo } }
        }
    //- main.rs
        #[macro_use]
        extern crate dep_lib_target;

        foo!();

        fn main() {
            foo().bar()
        }       //^ dep-lib/lib.rs
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar passed to another macro in a different crate (macro expanded to import)`() = stubOnlyResolve("""
    //- trans-lib/lib.rs
        #[macro_export]
        macro_rules! def_fn {
            ($ path:path) => { use $ path; }
        }
    //- dep-lib/lib.rs
        #[macro_use]
        extern crate trans_lib;
        pub use trans_lib::def_fn;
        pub fn func() {}
             //X
        #[macro_export]
        macro_rules! foo {
            () => { def_fn! { $ crate::func } }
        }
    //- main.rs
        #[macro_use]
        extern crate dep_lib_target;

        foo!(); // def_fn! { IntellijRustDollarCrate_dep_lib::func }

        fn main() {
            func();
        } //^ dep-lib/lib.rs
    """)

    fun `test expand macro inside stubbed file`() = stubOnlyResolve("""
    //- bar.rs
        pub struct S;
        impl S { pub fn bar(&self) {} }
        foo!();
    //- main.rs
        macro_rules! foo {
            () => {
                fn foo() -> S {}
            }
        }
        mod bar;
        fn main() {
            bar::foo().bar();
        }            //^ bar.rs
    """)

    fun `test impl defined by macro`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident) => {
                impl $ i {
                    fn foo(&self) -> Bar {}
                }
            }
        }

        struct Foo;
        struct Bar;
        foo!(Foo);
        impl Bar { fn bar(&self) {} }
                    //X
        fn main() {
            Foo.foo().bar();
        }           //^
    """)

    fun `test impl defined by macro with method defined by nested macro`() = checkByCode("""
        macro_rules! bar {
            ($ i:ident, $ j:ty) => { fn $ i(&self) -> $ j { unimplemented!() } }
        }

        macro_rules! foo {
            ($ i:ident) => {
                impl $ i {
                    bar!(foo, Bar);
                }
            }
        }

        struct Foo;
        struct Bar;
        foo!(Foo);
        impl Bar { fn bar(&self) {} }
                    //X
        fn main() {
            Foo.foo().bar();
        }           //^
    """)

    fun `test mod declared with macro`() = stubOnlyResolve("""
    //- main.rs
        macro_rules! foo {
            () => { mod child; };
        }

        foo!();

        pub struct S;
    //- child.rs
        use super::S;
                 //^ main.rs
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test mod declared with macro inside inline expanded mod`() = stubOnlyResolve("""
    //- main.rs
        macro_rules! gen_mod_decl_item {
            () => { mod foo2; };
        }
        macro_rules! gen_mod_item {
            () => { mod foo1 { gen_mod_decl_item!(); } };
        }

        gen_mod_item!();

        use foo1::foo2::S;
        fn func(_: S) {}
                 //^ foo1/foo2.rs
    //- foo1/foo2.rs
        pub struct S;
                 //X
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test mod with path attribute declared with macro`() = stubOnlyResolve("""
    //- main.rs
        macro_rules! foo {
            () => { #[path="foo.rs"] mod child; };
        }

        foo!();
    //- foo.rs
        fn func() {}
             //X
        mod inner {
            use super::func;
            fn main() {
                func();
            } //^ foo.rs
        }
    """)

    fun `test resolve item expanded from stmt context macro`() = checkByCode("""
        macro_rules! foo {
            ($ i:item) => ( $ i )
        }
        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X
        fn main() {
            foo! {
                fn foo() -> Foo { Foo }
            }
            foo().bar();
        }       //^
    """)

    fun `test resolve binding from stmt context macro`() = checkByCode("""
        macro_rules! foo {
            ($ i:stmt) => ( $ i )
        }
        fn main() {
            foo! {
                let a = 0;
            }     //X
            let _ = a;
        }         //^
    """)

    fun `test hygiene 1`() = checkByCode("""
        macro_rules! foo {
            () => ( let a = 0; )
        }
        fn main() {
            foo!();
            let _ = a;
        }         //^ unresolved
    """)

    fun `test hygiene 2`() = checkByCode("""
        macro_rules! foo {
            ($ i:ident) => ( let $ i = 0; )
        }
        macro_rules! bar {
            ($($ t:tt)*) => { $($ t)* };
        }
        fn main() {
            bar! {
                foo!(a);
                   //X
                let _ = a;
            }         //^
        }
    """)

    fun `test hygiene 3`() = checkByCode("""
        macro_rules! foo {
            () => ( let a = 0; )
        }
        macro_rules! bar {
            ($($ t:tt)*) => { $($ t)* };
        }
        fn main() {
            bar! {
                foo!();
                let _ = a;
            }         //^ unresolved
        }
    """)

    fun `test hygiene 4`() = checkByCode("""
        macro_rules! foo {
            () => ( let a = 0; )
        }
        macro_rules! bar {
            ($($ t:tt)*) => { $($ t)* };
        }
        fn main() {
            let a = 1;
              //X
            bar! {
                foo!();
                let _ = a;
            }         //^
        }
    """)

    fun `test hygiene 5`() = checkByCode("""
        macro_rules! bar {
            ($($ t:tt)*) => { $($ t)* };
        }
        fn main() {
            let a = 1;
              //X
            bar! {
                let _ = a;
            }         //^
            let a = 2;
        }
    """)

    fun `test resolve generic impl from impl trait`() = checkByCode("""
        macro_rules! foo {
            ($($ t:tt)*) => { $($ t)* };
        }
        trait Foo {}
        trait Bar { fn bar(&self) {} }
                     //X
        foo! { impl<T: Foo> Bar for T {} }
        fn foo() -> impl Foo { unimplemented!() }
        fn main() {
            foo().bar();
        }       //^
    """)

    fun `test resolve module under macro chain`() = stubOnlyResolve("""
    //- main.rs
        macro_rules! if_std {
            ($ i:item) => (
                $ i
            )
        }
        if_std! { pub mod foo; }
        fn main() {
            let a = foo::bar::baz::Baz;
                                  //^ foo/bar/baz.rs
        }
    //- foo/mod.rs
        if_std! { pub mod bar; }
    //- foo/bar/mod.rs
        if_std! { pub mod baz; }
    //- foo/bar/baz.rs
        if_std! {
            pub struct Baz;
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @BothEditions
    fun `test local_inner_macros`() = stubOnlyResolve("""
    //- main.rs
        extern crate test_package;
        use test_package::foo;
        macro_rules! bar {
            () => { use Bar as Baz; };
        }

        foo! { bar!{} } // Expands to `use Foo as Bar; use Bar as Baz;`

        struct Foo; // <-- resolves here
        type T = Baz;
               //^ main.rs
    //- lib.rs
        #[macro_export(local_inner_macros)]
        macro_rules! foo {
            ($ i:item) => { bar!{} $ i };
        }

        #[macro_export]
        macro_rules! bar {
            () => { use Foo as Bar; };
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test expand macro with incomplete path`() = stubOnlyResolve("""
    //- main.rs
        macro_rules! gen_func {
            () => { fn func() {} };
        }

        gen_func::!();

        fn main() {
            // here we just check that incomplete path doesn't cause exceptions
            func();
        } //^ unresolved
    """)

    @UseNewResolve
    fun `test legacy textual macro reexported as macro 2`() = checkByCode("""
        mod inner {
            #[macro_export]
            macro_rules! as_is_ {
                ($ i:item) => { $ i }
            }
            pub use as_is_ as as_is;
        }

        inner::as_is! { fn foo() {} }
                         //X

        fn main() {
            foo();
        } //^
    """)

    // when resolving macro call expanded from other macro call,
    // firstly left sibling expanded elements should be processed
    fun `test macro call expanded to macro def and macro call`() = checkByCode("""
        macro_rules! foo {
            (1) => {
                macro_rules! foo {
                    (2) => { use inner::func; };
                }
                foo!(2);
            };
            (2) => {};
        }

        mod inner {
            pub fn func() {}
        }        //X
        foo!(1);

        fn main() {
            func();
        } //^
    """)
}
