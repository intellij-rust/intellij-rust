/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

class AutoImportFixTest : AutoImportFixTestBase() {

    fun `test import struct`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test import enum variant 1`() = checkAutoImportFixByText("""
        mod foo {
            pub enum Foo { A }
        }

        fn main() {
            <error descr="Unresolved reference: `Foo`">Foo::A/*caret*/</error>;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub enum Foo { A }
        }

        fn main() {
            Foo::A/*caret*/;
        }
    """)

    fun `test import enum variant 2`() = checkAutoImportFixByText("""
        mod foo {
            pub enum Foo { A }
        }

        fn main() {
            let a = <error descr="Unresolved reference: `A`">A/*caret*/</error>;
        }
    """, """
        use foo::Foo::A;

        mod foo {
            pub enum Foo { A }
        }

        fn main() {
            let a = A/*caret*/;
        }
    """)

    fun `test import function`() = checkAutoImportFixByText("""
        mod foo {
            pub fn bar() -> i32 { unimplemented!() }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `bar`">bar/*caret*/</error>();
        }
    """, """
        use foo::bar;

        mod foo {
            pub fn bar() -> i32 { unimplemented!() }
        }

        fn main() {
            let f = bar/*caret*/();
        }
    """)

    fun `test import function method`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn foo() {}
            }
        }

        fn main() {
            <error descr="Unresolved reference: `Foo`">Foo::foo/*caret*/</error>();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn foo() {}
            }
        }

        fn main() {
            Foo::foo/*caret*/();
        }
    """)

    fun `test import generic item`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo<T>(T);
        }

        fn f<T>(foo: <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error><T>) {}
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo<T>(T);
        }

        fn f<T>(foo: Foo/*caret*/<T>) {}
    """)

    fun `test import module`() = checkAutoImportFixByText("""
        mod foo {
            pub mod bar {
                pub fn foo_bar() -> i32 { unimplemented!() }
            }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `bar`">bar/*caret*/::foo_bar</error>();
        }
    """, """
        use foo::bar;

        mod foo {
            pub mod bar {
                pub fn foo_bar() -> i32 { unimplemented!() }
            }
        }

        fn main() {
            let f = bar/*caret*/::foo_bar();
        }
    """)

    fun `test insert use item after existing use items`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        use foo::Bar;

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        use foo::Bar;
        use foo::Foo;

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test insert use item after inner attributes`() = checkAutoImportFixByText("""
        #![allow(non_snake_case)]

        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        #![allow(non_snake_case)]

        use foo::Foo;

        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test import item from nested module`() = checkAutoImportFixByText("""
        mod foo {
            pub mod bar {
                pub struct Foo;
            }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use foo::bar::Foo;

        mod foo {
            pub mod bar {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test don't try to import private item`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            struct Foo;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """)

    fun `test don't try to import from private mod`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            mod bar {
                pub struct Foo;
            }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """)

    fun `test complex module structure`() = checkAutoImportFixByText("""
        mod aaa {
            mod bbb {
                fn foo() {
                    let x = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
                }
            }
        }
        mod ccc {
            pub mod ddd {
                pub struct Foo;
            }
            mod eee {
                struct Foo;
            }
        }
    """, """
        mod aaa {
            mod bbb {
                use ccc::ddd::Foo;

                fn foo() {
                    let x = Foo/*caret*/;
                }
            }
        }
        mod ccc {
            pub mod ddd {
                pub struct Foo;
            }
            mod eee {
                struct Foo;
            }
        }
    """)

    fun `test complex module structure with file modules`() = checkAutoImportFixByFileTree("""
        //- aaa/mod.rs
        mod bbb;
        //- aaa/bbb/mod.rs
        fn foo() {
            let x = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
        //- ccc/mod.rs
        pub mod ddd;
        mod eee;
        //- ccc/ddd/mod.rs
        pub struct Foo;
        //- ccc/eee/mod.rs
        struct Foo;
        //- main.rs
        mod aaa;
        mod ccc;
    """, """
        //- aaa/bbb/mod.rs
        use ccc::ddd::Foo;

        fn foo() {
            let x = Foo/*caret*/;
        }
    """)

    fun `test import module declared via module declaration`() = checkAutoImportFixByFileTree("""
        //- foo/bar.rs
        fn foo_bar() {}
        //- main.rs
        mod foo {
            pub mod bar;
        }
        fn main() {
            <error descr="Unresolved reference: `bar`">bar::foo_bar/*caret*/</error>();
        }
    """, """
        //- main.rs
        use foo::bar;

        mod foo {
            pub mod bar;
        }
        fn main() {
            bar::foo_bar/*caret*/();
        }
    """)

    fun `test filter import candidates 1`() = checkAutoImportFixByText("""
        mod foo1 {
            pub fn bar() {}
        }

        mod foo2 {
            pub mod bar {
                pub fn foo_bar() {}
            }
        }

        fn main() {
            <error descr="Unresolved reference: `bar`">bar/*caret*/</error>();
        }
    """, """
        use foo1::bar;

        mod foo1 {
            pub fn bar() {}
        }

        mod foo2 {
            pub mod bar {
                pub fn foo_bar() {}
            }
        }

        fn main() {
            bar/*caret*/();
        }
    """)

    fun `test filter import candidates 2`() = checkAutoImportFixByText("""
        mod foo1 {
            pub fn bar() {}
        }

        mod foo2 {
            pub mod bar {
                pub fn foo_bar() {}
            }
        }

        fn main() {
            <error descr="Unresolved reference: `bar`">bar::foo_bar/*caret*/</error>();
        }
    """, """
        use foo2::bar;

        mod foo1 {
            pub fn bar() {}
        }

        mod foo2 {
            pub mod bar {
                pub fn foo_bar() {}
            }
        }

        fn main() {
            bar::foo_bar/*caret*/();
        }
    """)

    fun `test filter members without owner prefix`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn foo() {}
            }
        }

        fn main() {
            <error descr="Unresolved reference: `foo`">foo/*caret*/</error>();
        }
    """)

    fun `test don't try to import item if it can't be resolved`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub mod bar {
            }
        }
        fn main() {
            <error descr="Unresolved reference: `bar`">bar::foo_bar/*caret*/</error>();
        }
    """)

    fun `test don't import trait method`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Bar {
                fn bar();
            }
        }
        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar::bar/*caret*/</error>();
        }
    """)

    fun `test don't import trait const`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Bar {
                const BAR: i32;
            }
        }
        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar::BAR/*caret*/</error>();
        }
    """)

    fun `test import reexported item`() = checkAutoImportFixByText("""
        mod foo {
            mod bar {
                pub struct Bar;
            }

            pub use self::bar::Bar;
        }

        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
        }
    """, """
        use foo::Bar;

        mod foo {
            mod bar {
                pub struct Bar;
            }

            pub use self::bar::Bar;
        }

        fn main() {
            Bar/*caret*/;
        }
    """)

    fun `test import reexported item with alias`() = checkAutoImportFixByText("""
        mod foo {
            mod bar {
                pub struct Bar;
            }

            pub use self::bar::Bar as Foo;
        }

        fn main() {
            <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use foo::Foo;

        mod foo {
            mod bar {
                pub struct Bar;
            }

            pub use self::bar::Bar as Foo;
        }

        fn main() {
            Foo/*caret*/;
        }
    """)

    fun `test import reexported item via use group`() = checkAutoImportFixByText("""
        mod foo {
            mod bar {
                pub struct Baz;
                pub struct Qwe;
            }

            pub use self::bar::{Baz, Qwe};
        }

        fn main() {
            let a = <error descr="Unresolved reference: `Baz`">Baz/*caret*/</error>;
        }
    """, """
        use foo::Baz;

        mod foo {
            mod bar {
                pub struct Baz;
                pub struct Qwe;
            }

            pub use self::bar::{Baz, Qwe};
        }

        fn main() {
            let a = Baz/*caret*/;
        }
    """)

    fun `test import reexported item via 'self'`() = checkAutoImportFixByText("""
        mod foo {
            mod bar {
                pub struct Baz;
            }

            pub use self::bar::Baz::{self};
        }

        fn main() {
            let a = <error descr="Unresolved reference: `Baz`">Baz/*caret*/</error>;
        }
    """, """
        use foo::Baz;

        mod foo {
            mod bar {
                pub struct Baz;
            }

            pub use self::bar::Baz::{self};
        }

        fn main() {
            let a = Baz/*caret*/;
        }
    """)

    fun `test import reexported item with complex reexport`() = checkAutoImportFixByText("""
        mod foo {
            mod bar {
                pub struct Baz;
                pub struct Qwe;
            }

            pub use self::bar::{Baz as Foo, Qwe};
        }

        fn main() {
            let a = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use foo::Foo;

        mod foo {
            mod bar {
                pub struct Baz;
                pub struct Qwe;
            }

            pub use self::bar::{Baz as Foo, Qwe};
        }

        fn main() {
            let a = Foo/*caret*/;
        }
    """)

    fun `test module reexport`() = checkAutoImportFixByText("""
        mod foo {
            mod bar {
                pub mod baz {
                    pub struct FooBar;
                }
            }

            pub use self::bar::baz;
        }

        fn main() {
            let x = <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>;
        }
    """, """
        use foo::baz::FooBar;

        mod foo {
            mod bar {
                pub mod baz {
                    pub struct FooBar;
                }
            }

            pub use self::bar::baz;
        }

        fn main() {
            let x = FooBar/*caret*/;
        }
    """)

    fun `test do not import path in use item`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub struct Foo;
        }

        mod bar {
            pub struct Bar;
        }

        use foo::{Foo, <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>};
    """, AutoImportFix.Testmarks.pathInUseItem)

    fun `test multiple import`() = checkAutoImportFixByTextWithMultipleChoice("""
        mod foo {
            pub struct Foo;
            pub mod bar {
                pub struct Foo;
            }
        }

        mod baz {
            pub struct Foo;
            mod qwe {
                pub struct Foo;
            }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, setOf("foo::Foo", "foo::bar::Foo", "baz::Foo"), "foo::bar::Foo", """
        use foo::bar::Foo;

        mod foo {
            pub struct Foo;
            pub mod bar {
                pub struct Foo;
            }
        }

        mod baz {
            pub struct Foo;
            mod qwe {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test multiple import with reexports`() = checkAutoImportFixByTextWithMultipleChoice("""
        mod foo {
            pub struct Foo;
        }

        mod bar {
            mod baz {
                pub struct Foo;
            }

            pub use self::baz::Foo;
        }

        mod qwe {
            mod xyz {
                pub struct Bar;
            }

            pub use self::xyz::Bar as Foo;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, setOf("foo::Foo", "bar::Foo", "qwe::Foo"), "qwe::Foo", """
        use qwe::Foo;

        mod foo {
            pub struct Foo;
        }

        mod bar {
            mod baz {
                pub struct Foo;
            }

            pub use self::baz::Foo;
        }

        mod qwe {
            mod xyz {
                pub struct Bar;
            }

            pub use self::xyz::Bar as Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test double module reexport`() = checkAutoImportFixByTextWithMultipleChoice("""
        mod foo {
            pub mod bar {
                pub struct FooBar;
            }
        }

        mod baz {
            pub mod qqq {
                pub use foo::bar;
            }
        }

        mod xxx {
            pub use baz::qqq;
        }

        fn main() {
            let a = <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>;
        }
    """, setOf("foo::bar::FooBar", "baz::qqq::bar::FooBar", "xxx::qqq::bar::FooBar"), "baz::qqq::bar::FooBar", """
        use baz::qqq::bar::FooBar;

        mod foo {
            pub mod bar {
                pub struct FooBar;
            }
        }

        mod baz {
            pub mod qqq {
                pub use foo::bar;
            }
        }

        mod xxx {
            pub use baz::qqq;
        }

        fn main() {
            let a = FooBar/*caret*/;
        }
    """)

    fun `test cyclic module reexports`() = checkAutoImportFixByTextWithMultipleChoice("""
        pub mod x {
            pub struct Z;
            pub use y;
        }

        pub mod y {
            pub use x;
        }

        fn main() {
            let x = <error descr="Unresolved reference: `Z`">Z/*caret*/</error>;
        }
    """, setOf("x::Z", "y::x::Z", "x::y::x::Z"), "x::Z", """
        use x::Z;

        pub mod x {
            pub struct Z;
            pub use y;
        }

        pub mod y {
            pub use x;
        }

        fn main() {
            let x = Z/*caret*/;
        }
    """)

    fun `test crazy cyclic module reexports`() = checkAutoImportFixByTextWithMultipleChoice("""
        pub mod x {
            pub use u;
            pub mod y {
                pub use u::v;
                pub struct Z;
            }
        }

        pub mod u {
            pub use x::y;
            pub mod v {
                pub use x;
            }
        }

        fn main() {
            let z = <error descr="Unresolved reference: `Z`">Z/*caret*/</error>;
        }
    """, setOf(
        "x::y::Z",
        "x::u::y::Z",
        "x::u::v::x::y::Z",
        "x::u::y::v::x::y::Z",
        "x::y::v::x::u::y::Z",
        "x::y::v::x::y::Z",
        "u::y::Z",
        "u::v::x::y::Z",
        "u::y::v::x::y::Z",
        "u::v::x::u::y::Z"
    ), "u::y::Z", """
        use u::y::Z;

        pub mod x {
            pub use u;
            pub mod y {
                pub use u::v;
                pub struct Z;
            }
        }

        pub mod u {
            pub use x::y;
            pub mod v {
                pub use x;
            }
        }

        fn main() {
            let z = Z/*caret*/;
        }
    """)

    fun `test filter imports`() = checkAutoImportFixByTextWithMultipleChoice("""
        mod foo {
            pub mod bar {
                pub struct FooBar;
            }

            pub use self::bar::FooBar;
        }

        mod baz {
            pub use foo::bar::FooBar;
        }

        mod quuz {
            pub use foo::bar;
        }

        fn main() {
            let x = <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>;
        }
    """, setOf("foo::FooBar", "baz::FooBar", "quuz::bar::FooBar"), "baz::FooBar", """
        use baz::FooBar;

        mod foo {
            pub mod bar {
                pub struct FooBar;
            }

            pub use self::bar::FooBar;
        }

        mod baz {
            pub use foo::bar::FooBar;
        }

        mod quuz {
            pub use foo::bar;
        }

        fn main() {
            let x = FooBar/*caret*/;
        }
    """)

    fun `test filter by namespace - type`() = checkAutoImportFixByText("""
        mod struct_mod {
            pub struct Foo { foo: i32 }
        }

        mod enum_mod {
            pub enum Bar {
                Foo
            }
        }

        fn foo(x: <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>) {}
    """, """
        use struct_mod::Foo;

        mod struct_mod {
            pub struct Foo { foo: i32 }
        }

        mod enum_mod {
            pub enum Bar {
                Foo
            }
        }

        fn foo(x: Foo/*caret*/) {}
    """)

    // should suggest only `enum_mod::Bar::Foo`
    fun `test filter by namespace - value`() = checkAutoImportFixByTextWithMultipleChoice("""
        mod struct_mod {
            pub struct Foo { foo: i32 }
        }

        mod enum_mod {
            pub enum Bar {
                Foo
            }
        }

        mod trait_mod {
            pub trait Foo {}
        }

        mod type_alias_mod {
            pub type Foo = Bar;
            struct Bar { x: i32 }
        }

        fn main() {
            let x = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, setOf("struct_mod::Foo", "enum_mod::Bar::Foo", "type_alias_mod::Foo"), "enum_mod::Bar::Foo", """
        use enum_mod::Bar::Foo;

        mod struct_mod {
            pub struct Foo { foo: i32 }
        }

        mod enum_mod {
            pub enum Bar {
                Foo
            }
        }

        mod trait_mod {
            pub trait Foo {}
        }

        mod type_alias_mod {
            pub type Foo = Bar;
            struct Bar { x: i32 }
        }

        fn main() {
            let x = Foo/*caret*/;
        }
    """)

    fun `test filter by namespace - trait`() = checkAutoImportFixByText("""
        mod struct_mod {
            pub struct Foo { foo: i32 }
        }

        mod enum_mod {
            pub enum Bar {
                Foo
            }
        }

        mod trait_mod {
            pub trait Foo {}
        }

        fn foo<T: <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>>(x: T) {}
    """, """
        use trait_mod::Foo;

        mod struct_mod {
            pub struct Foo { foo: i32 }
        }

        mod enum_mod {
            pub enum Bar {
                Foo
            }
        }

        mod trait_mod {
            pub trait Foo {}
        }

        fn foo<T: Foo/*caret*/>(x: T) {}
    """)

    fun `test filter by namespace - struct literal`() = checkAutoImportFixByTextWithMultipleChoice("""
        mod struct_mod {
            pub struct Foo;
        }

        mod block_struct_mod {
            pub struct Foo { x: i32 }
        }

        mod enum_mod {
            pub enum Bar {
                Foo
            }
        }

        mod trait_mod {
            pub trait Foo {}
        }

        mod enum_struct_mod {
            pub enum Bar {
                Foo { foo: i32 }
            }
        }

        mod type_alias_mod {
            pub type Foo = Bar;
            struct Bar { x: i32 }
        }

        fn main() {
            let x = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error> { };
        }
    """, setOf("block_struct_mod::Foo", "enum_struct_mod::Bar::Foo", "type_alias_mod::Foo"), "enum_struct_mod::Bar::Foo", """
        use enum_struct_mod::Bar::Foo;

        mod struct_mod {
            pub struct Foo;
        }

        mod block_struct_mod {
            pub struct Foo { x: i32 }
        }

        mod enum_mod {
            pub enum Bar {
                Foo
            }
        }

        mod trait_mod {
            pub trait Foo {}
        }

        mod enum_struct_mod {
            pub enum Bar {
                Foo { foo: i32 }
            }
        }

        mod type_alias_mod {
            pub type Foo = Bar;
            struct Bar { x: i32 }
        }

        fn main() {
            let x = Foo/*caret*/ { };
        }
    """)

    fun `test import trait method`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }
        }

        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo/*caret*/</error>();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }
        }

        fn main() {
            let x = 123.foo/*caret*/();
        }
    """)

    fun `test import default trait method`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self) {}
            }

            impl<T> Foo for T {}
        }

        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo/*caret*/</error>();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub trait Foo {
                fn foo(&self) {}
            }

            impl<T> Foo for T {}
        }

        fn main() {
            let x = 123.foo/*caret*/();
        }
    """)

    fun `test import reexported trait method`() = checkAutoImportFixByText("""
        mod foo {
            mod bar {
                pub mod baz {
                    pub trait FooBar {
                        fn foo_bar(&self);
                    }

                    impl<T> FooBar for T {
                        fn foo_bar(&self) {}
                    }
                }
            }

            pub use self::bar::baz;
        }

        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo_bar`">foo_bar/*caret*/</error>();
        }
    """, """
        use foo::baz::FooBar;

        mod foo {
            mod bar {
                pub mod baz {
                    pub trait FooBar {
                        fn foo_bar(&self);
                    }

                    impl<T> FooBar for T {
                        fn foo_bar(&self) {}
                    }
                }
            }

            pub use self::bar::baz;
        }

        fn main() {
            let x = 123.foo_bar/*caret*/();
        }
    """)

    fun `test do not try to import non trait method`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }
        }

        struct Bar;

        impl Bar {
            fn foo(&self) {}
        }

        fn main() {
            let x = Bar.foo/*caret*/();
        }
    """)

    fun `test multiple trait method import`() = checkAutoImportFixByTextWithMultipleChoice("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            pub trait Bar {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }

            impl<T> Bar for T {
                fn foo(&self) {}
            }
        }

        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo/*caret*/</error>();
        }
    """, setOf("foo::Foo", "foo::Bar"), "foo::Bar", """
        use foo::Bar;

        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            pub trait Bar {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }

            impl<T> Bar for T {
                fn foo(&self) {}
            }
        }

        fn main() {
            let x = 123.foo/*caret*/();
        }
    """)
}
