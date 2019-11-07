/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.ExpandMacros
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor

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

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test 'crate' metavar`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
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
    fun `test 'crate' metavar with alias`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
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
    fun `test 'crate' metavar with macro call not in crate root`() = stubOnlyResolve("""
    //- lib.rs
        pub struct Foo;
        impl Foo {
            pub fn bar(&self) {}
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

    fun `test expand macro inside stubbed file`() = stubOnlyResolve("""
    //- bar.rs
        pub struct S;
        impl S { fn bar(&self) {} }
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
}
