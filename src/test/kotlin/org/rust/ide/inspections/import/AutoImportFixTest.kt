/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.utils.import.Testmarks

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
            <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>::A;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub enum Foo { A }
        }

        fn main() {
            Foo/*caret*/::A;
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

    fun `test import enum variant 3`() = checkAutoImportFixByText("""
        enum Foo { A }

        fn main() {
            let a = <error descr="Unresolved reference: `A`">A/*caret*/</error>;
        }
    """, """
        use Foo::A;

        enum Foo { A }

        fn main() {
            let a = A/*caret*/;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import enum variant of reexported enum`() = checkAutoImportFixByText("""
        mod inner1 {
            pub use inner2::Foo;

            mod inner2 {
                pub enum Foo { A }
            }
        }

        fn main() {
            let _ = <error descr="Unresolved reference: `A`">A/*caret*/</error>;
        }
    """, """
        use crate::inner1::Foo::A;

        mod inner1 {
            pub use inner2::Foo;

            mod inner2 {
                pub enum Foo { A }
            }
        }

        fn main() {
            let _ = A;
        }
    """)

    fun `test import function`() = checkAutoImportFixByText("""
        mod foo {
            pub fn bar() -> i32 { 0 }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `bar`">bar/*caret*/</error>();
        }
    """, """
        use foo::bar;

        mod foo {
            pub fn bar() -> i32 { 0 }
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
            <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>::foo();
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
            Foo/*caret*/::foo();
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

    fun `test import item with type params (struct literal)`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo<T> { x: T }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>::<i32> {};
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo<T> { x: T }
        }

        fn main() {
            let f = Foo/*caret*/::<i32> {};
        }
    """)

    fun `test import item with type params (tuple struct literal)`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo<T>(T);
            impl<T> Foo<T> {
                fn bar() {}
            }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>::<i32>::bar();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo<T>(T);
            impl<T> Foo<T> {
                fn bar() {}
            }
        }

        fn main() {
            let f = Foo/*caret*/::<i32>::bar();
        }
    """)

    fun `test import item with type params (pat struct)`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo<T> { x: T }
        }

        fn main() {
            let <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>::<i32> { x } = ();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo<T> { x: T }
        }

        fn main() {
            let Foo/*caret*/::<i32> { x } = ();
        }
    """)

    fun `test import item with type params (pat tuple struct)`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo<T>(T);
            impl<T> Foo<T> {
                fn bar() {}
            }
        }

        fn main() {
            let <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>::<i32>::bar(x) = ();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub struct Foo<T>(T);
            impl<T> Foo<T> {
                fn bar() {}
            }
        }

        fn main() {
            let Foo/*caret*/::<i32>::bar(x) = ();
        }
    """)

    fun `test import module`() = checkAutoImportFixByText("""
        mod foo {
            pub mod bar {
                pub fn foo_bar() -> i32 { 0 }
            }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `bar`">bar/*caret*/</error>::foo_bar();
        }
    """, """
        use foo::bar;

        mod foo {
            pub mod bar {
                pub fn foo_bar() -> i32 { 0 }
            }
        }

        fn main() {
            let f = bar/*caret*/::foo_bar();
        }
    """)

    fun `test insert use item after existing use items`() = checkAutoImportFixByText("""
        use foo::Bar;

        mod foo {
            pub struct Bar;
        }

        mod bar {
            pub struct Foo;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use bar::Foo;
        use foo::Bar;

        mod foo {
            pub struct Bar;
        }

        mod bar {
            pub struct Foo;
        }

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

    fun `test insert use item after outer attributes`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo;
        }

        mod tests {
            fn foo() {
                let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
            }
        }
    """, """
        mod foo {
            pub struct Foo;
        }

        mod tests {
            use foo::Foo;

            fn foo() {
                let f = Foo/*caret*/;
            }
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

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import private item from parent mod`() = checkAutoImportFixByText("""
        mod a1 {
            struct Foo;

            mod a2 {
                fn main() {
                    let _ = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
                }
            }
        }
    """, """
        mod a1 {
            struct Foo;

            mod a2 {
                use crate::a1::Foo;

                fn main() {
                    let _ = Foo;
                }
            }
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test don't try to import private reexport from parent mod 1`() = checkAutoImportFixByText("""
        mod a1 {
            use crate::b1::b2::Foo;

            mod a2 {
                fn main() {
                    let _ = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
                }
            }
        }
        mod b1 {
            pub mod b2 {
                pub struct Foo;
            }
        }
    """, """
        mod a1 {
            use crate::b1::b2::Foo;

            mod a2 {
                use crate::b1::b2::Foo;

                fn main() {
                    let _ = Foo;
                }
            }
        }
        mod b1 {
            pub mod b2 {
                pub struct Foo;
            }
        }
    """, Testmarks.ignorePrivateImportInParentMod)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test don't try to import private reexport from parent mod 2`() = checkAutoImportFixByText("""
        mod a1 {
            use crate::b1::b2::b3;

            mod a2 {
                fn main() {
                    let _ = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
                }
            }
        }
        mod b1 {
            pub mod b2 {
                pub mod b3 {
                    pub struct Foo;
                }
            }
        }
    """, """
        mod a1 {
            use crate::b1::b2::b3;

            mod a2 {
                use crate::b1::b2::b3::Foo;

                fn main() {
                    let _ = Foo;
                }
            }
        }
        mod b1 {
            pub mod b2 {
                pub mod b3 {
                    pub struct Foo;
                }
            }
        }
    """, Testmarks.ignorePrivateImportInParentMod)

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
            <error descr="Unresolved reference: `bar`">bar/*caret*/</error>::foo_bar();
        }
    """, """
        //- main.rs
        use foo::bar;

        mod foo {
            pub mod bar;
        }
        fn main() {
            bar/*caret*/::foo_bar();
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
            <error descr="Unresolved reference: `bar`">bar/*caret*/</error>::foo_bar();
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
            bar/*caret*/::foo_bar();
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

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import item if it can't be resolved`() = checkAutoImportFixByText("""
        mod foo {
            pub mod bar {
            }
        }
        fn main() {
            <error descr="Unresolved reference: `bar`">bar/*caret*/</error>::unresolved();
        }
    """, """
        use crate::foo::bar;

        mod foo {
            pub mod bar {
            }
        }
        fn main() {
            bar::unresolved();
        }
    """)

    fun `test don't import trait assoc function if its import is useless`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Bar {
                fn bar();
            }
        }
        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>::bar();
        }
    """)

    fun `test trait method with self parameter`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Bar {
                fn bar(&self);
            }
        }
        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>::bar();
        }
    """, """
        use foo::Bar;

        mod foo {
            pub trait Bar {
                fn bar(&self);
            }
        }
        fn main() {
            Bar::bar();
        }
    """)

    fun `test trait method with parameter contains Self type`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Bar {
                fn bar() -> Self;
            }
        }
        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>::bar();
        }
    """, """
        use foo::Bar;

        mod foo {
            pub trait Bar {
                fn bar() -> Self;
            }
        }
        fn main() {
            Bar/*caret*/::bar();
        }
    """)

    fun `test don't import trait associated const`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Bar {
                const BAR: i32;
            }
        }
        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>::BAR();
        }
    """)

    fun `test trait const containing Self type`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Bar {
                const C: Self;
            }
        }
        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>::C;
        }
    """, """
        use foo::Bar;

        mod foo {
            pub trait Bar {
                const C: Self;
            }
        }
        fn main() {
            Bar/*caret*/::C;
        }
    """)

    fun `test don't import trait associated type`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Bar {
                type Item;
            }
        }
        fn main() {
            let _: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>::Item;
        }
    """)

    fun `test import reexported item`() = checkAutoImportFixByText("""
        mod foo {
            pub use self::bar::Bar;

            mod bar {
                pub struct Bar;
            }
        }

        fn main() {
            <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
        }
    """, """
        use foo::Bar;

        mod foo {
            pub use self::bar::Bar;

            mod bar {
                pub struct Bar;
            }
        }

        fn main() {
            Bar/*caret*/;
        }
    """)

    fun `test import reexported item with alias`() = checkAutoImportFixByText("""
        mod foo {
            pub use self::bar::Bar as Foo;

            mod bar {
                pub struct Bar;
            }
        }

        fn main() {
            <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub use self::bar::Bar as Foo;

            mod bar {
                pub struct Bar;
            }
        }

        fn main() {
            Foo/*caret*/;
        }
    """)

    fun `test import reexported item via use group`() = checkAutoImportFixByText("""
        mod foo {
            pub use self::bar::{Baz, Qwe};

            mod bar {
                pub struct Baz;
                pub struct Qwe;
            }
        }

        fn main() {
            let a = <error descr="Unresolved reference: `Baz`">Baz/*caret*/</error>;
        }
    """, """
        use foo::Baz;

        mod foo {
            pub use self::bar::{Baz, Qwe};

            mod bar {
                pub struct Baz;
                pub struct Qwe;
            }
        }

        fn main() {
            let a = Baz/*caret*/;
        }
    """)

    fun `test import reexported item via 'self'`() = checkAutoImportFixByText("""
        mod foo {
            pub use self::bar::Baz::{self};

            mod bar {
                pub struct Baz;
            }
        }

        fn main() {
            let a = <error descr="Unresolved reference: `Baz`">Baz/*caret*/</error>;
        }
    """, """
        use foo::Baz;

        mod foo {
            pub use self::bar::Baz::{self};

            mod bar {
                pub struct Baz;
            }
        }

        fn main() {
            let a = Baz/*caret*/;
        }
    """)

    fun `test import reexported item with complex reexport`() = checkAutoImportFixByText("""
        mod foo {
            pub use self::bar::{Baz as Foo, Qwe};

            mod bar {
                pub struct Baz;
                pub struct Qwe;
            }
        }

        fn main() {
            let a = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub use self::bar::{Baz as Foo, Qwe};

            mod bar {
                pub struct Baz;
                pub struct Qwe;
            }
        }

        fn main() {
            let a = Foo/*caret*/;
        }
    """)

    fun `test module reexport`() = checkAutoImportFixByText("""
        mod foo {
            pub use self::bar::baz;

            mod bar {
                pub mod baz {
                    pub struct FooBar;
                }
            }
        }

        fn main() {
            let x = <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>;
        }
    """, """
        use foo::baz::FooBar;

        mod foo {
            pub use self::bar::baz;

            mod bar {
                pub mod baz {
                    pub struct FooBar;
                }
            }
        }

        fn main() {
            let x = FooBar/*caret*/;
        }
    """)

    fun `test do not import path in use item`() = checkAutoImportFixIsUnavailable("""
        mod bar {
            pub struct Bar;
        }

        use <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
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
    """, listOf("baz::Foo", "foo::Foo", "foo::bar::Foo"), "baz::Foo", """
        use baz::Foo;

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
            pub use self::baz::Foo;

            mod baz {
                pub struct Foo;
            }
        }

        mod qwe {
            pub use self::xyz::Bar as Foo;

            mod xyz {
                pub struct Bar;
            }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, listOf( "bar::Foo","foo::Foo", "qwe::Foo"),  "bar::Foo", """
        use bar::Foo;

        mod foo {
            pub struct Foo;
        }

        mod bar {
            pub use self::baz::Foo;

            mod baz {
                pub struct Foo;
            }
        }

        mod qwe {
            pub use self::xyz::Bar as Foo;

            mod xyz {
                pub struct Bar;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test double module reexport`() = checkAutoImportFixByText("""
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
    """, """
        use foo::bar::FooBar;

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

    fun `test cyclic module reexports`() = checkAutoImportFixByText("""
        pub mod x {
            pub use y;

            pub struct Z;
        }

        pub mod y {
            pub use x;
        }

        fn main() {
            let x = <error descr="Unresolved reference: `Z`">Z/*caret*/</error>;
        }
    """, """
        use x::Z;

        pub mod x {
            pub use y;

            pub struct Z;
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
    """, listOf("u::y::Z", "x::y::Z"), "u::y::Z", """
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
            pub use self::bar::FooBar;

            pub mod bar {
                pub struct FooBar;
            }
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
    """, listOf("baz::FooBar", "foo::FooBar"), "baz::FooBar", """
        use baz::FooBar;

        mod foo {
            pub use self::bar::FooBar;

            pub mod bar {
                pub struct FooBar;
            }
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
    """, listOf("enum_mod::Bar::Foo", "struct_mod::Foo", "type_alias_mod::Foo"), "enum_mod::Bar::Foo", """
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
    """, listOf("block_struct_mod::Foo", "enum_struct_mod::Bar::Foo", "type_alias_mod::Foo"), "enum_struct_mod::Bar::Foo", """
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

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import item with correct namespace when multiple namespaces available`() = checkAutoImportFixByTextWithoutHighlighting("""
        mod inner {
            pub struct foo {}
            pub fn foo() {}
        }
        fn test(a: foo/*caret*/) {}
    """, """
        use crate::inner::foo;

        mod inner {
            pub struct foo {}
            pub fn foo() {}
        }
        fn test(a: foo/*caret*/) {}
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

    fun `test import trait method UFCS of primitive`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }
        }

        fn main() {
            let x = i32::<error descr="Unresolved reference: `foo`">foo/*caret*/</error>(123);
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
            let x = i32::foo/*caret*/(123);
        }
    """)

    fun `test import trait method UFCS of struct`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }
        }

        struct S;
        fn main() {
            let x = S::<error descr="Unresolved reference: `foo`">foo/*caret*/</error>(123);
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

        struct S;
        fn main() {
            let x = S::foo/*caret*/(123);
        }
    """)

    fun `test import trait method UFCS of explicit type-qualified path`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }
        }

        fn main() {
            let x = <i32>::<error descr="Unresolved reference: `foo`">foo/*caret*/</error>(123);
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
            let x = <i32>::foo/*caret*/(123);
        }
    """)

    fun `test import trait associated constant`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo {
                const C: i32;
            }

            impl<T> Foo for T {
                const C: i32 = 0;
            }
        }

        fn main() {
            let x = i32::<error descr="Unresolved reference: `C`">C/*caret*/</error>(123);
        }
    """, """
        use foo::Foo;

        mod foo {
            pub trait Foo {
                const C: i32;
            }

            impl<T> Foo for T {
                const C: i32 = 0;
            }
        }

        fn main() {
            let x = i32::C/*caret*/(123);
        }
    """)

    fun `test import trait default method UFCS`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self) {}
            }

            impl<T> Foo for T {}
        }

        fn main() {
            let x = i32::<error descr="Unresolved reference: `foo`">foo/*caret*/</error>(123);
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
            let x = i32::foo/*caret*/(123);
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import trait default method UFCS 2`() = checkAutoImportFixByFileTree("""
    //- lib.rs
        pub trait Trait {
            fn foo() {}
        }
        impl<T> Trait for T {}
    //- main.rs
        pub use test_package::Trait;

        mod inner {
            fn main() {
                i32::<error descr="Unresolved reference: `foo`">foo/*caret*/</error>();
            }
        }
    """, """
    //- lib.rs
        pub trait Trait {
            fn foo() {}
        }
        impl<T> Trait for T {}
    //- main.rs
        pub use test_package::Trait;

        mod inner {
            use test_package::Trait;

            fn main() {
                i32::foo();
            }
        }
    """)

    fun `test import trait default assoc function`() = checkAutoImportFixByText("""
        mod foo {
            pub struct S;
            pub trait Foo {
                fn foo() {}
            }

            impl<T> Foo for T {}
        }

        fn main() {
            let x = <error descr="Unresolved reference: `S`">S/*caret*/</error>::foo();
        }
    """, """
        use foo::S;

        mod foo {
            pub struct S;
            pub trait Foo {
                fn foo() {}
            }

            impl<T> Foo for T {}
        }

        fn main() {
            let x = S/*caret*/::foo();
        }
    """)

    fun `test import reexported trait method`() = checkAutoImportFixByText("""
        mod foo {
            pub use self::bar::baz;

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
        }

        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo_bar`">foo_bar/*caret*/</error>();
        }
    """, """
        use foo::baz::FooBar;

        mod foo {
            pub use self::bar::baz;

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
    """, listOf("foo::Bar", "foo::Foo"), "foo::Bar", """
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

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test import trait method, use direct path`() = checkAutoImportFixByFileTree("""
    //- dep-lib/lib.rs
        pub trait Foo {
            fn foo(&self) {}
        }
        impl<T> Foo for T {}
    //- lib.rs
        pub extern crate dep_lib_target;
    //- main.rs
        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo/*caret*/</error>();
        }
    """, """
    //- main.rs
        use dep_lib_target::Foo;

        fn main() {
            let x = 123.foo();
        }
    """)

    fun `test suggest single method`() = checkAutoImportFixByText("""
        struct Foo;
        struct Bar;

        #[lang="deref"]
        trait Deref {
            type Target;
        }

        impl Deref for Bar {
            type Target = Foo;
        }
        mod a {
            pub trait X {
                fn do_x(&self);
            }

            impl X for ::Foo {
                fn do_x(&self) {}
            }

            impl X for ::Bar {
                fn do_x(&self) {}
            }
        }

        fn main() {
            Bar.<error>do_x/*caret*/</error>();
        }
    """, """
        use a::X;

        struct Foo;
        struct Bar;

        #[lang="deref"]
        trait Deref {
            type Target;
        }

        impl Deref for Bar {
            type Target = Foo;
        }
        mod a {
            pub trait X {
                fn do_x(&self);
            }

            impl X for ::Foo {
                fn do_x(&self) {}
            }

            impl X for ::Bar {
                fn do_x(&self) {}
            }
        }

        fn main() {
            Bar.do_x/*caret*/();
        }
    """)

    /** Issue [2822](https://github.com/intellij-rust/intellij-rust/issues/2822) */
    fun `test do not try to import trait object method`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Foo {
                fn foo(&self) {}
            }
        }

        fn bar(t: &dyn foo::Foo) {
            t.foo/*caret*/();
        }
    """)

    fun `test do not try to import trait bound method`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Foo {
                fn foo(&self) {}
            }
        }

        fn bar<T: foo::Foo>(t: T) {
            t.foo/*caret*/();
        }
    """)

    /** Issue [2863](https://github.com/intellij-rust/intellij-rust/issues/2863) */
    fun `test do not try to import aliased trait`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Foo {
                fn foo(&self) {}
            }

            impl<T> Foo for T {}
        }

        use foo::Foo as _Foo;

        fn main() {
            123.foo/*caret*/();
        }
    """)

    fun `test do not try to import underscore aliased trait`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Foo {
                fn foo(&self) {}
            }

            impl<T> Foo for T {}
        }

        use foo::Foo as _;

        fn main() {
            123.foo/*caret*/();
        }
    """)

    fun `test do not try to import trait for method call inside impl of that trait`() = checkAutoImportFixIsUnavailable("""
        mod foo {
            pub trait Foo { fn foo(&self) {} }
        }

        struct S2;
        impl foo::Foo for S2 { fn foo(&self) {} }

        struct S1(S2);
        impl foo::Foo for S1 {
            fn foo(&self) {
                self.0.foo/*caret*/()
            }
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import item in root module (edition 2018)`() = checkAutoImportFixByText("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use crate::foo::Foo;

        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import item from root module (edition 2018)`() = checkAutoImportFixByText("""
        struct Foo;

        mod bar {
            type T = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        struct Foo;

        mod bar {
            use crate::Foo;

            type T = Foo;
        }
    """)

    fun `test import inside nested module`() = checkAutoImportFixByText("""
        mod b {
            pub struct S;
        }
        mod c {
            fn x() -> <error descr="Unresolved reference: `S`">S/*caret*/</error> {

            }
        }
    """, """
        mod b {
            pub struct S;
        }
        mod c {
            use b::S;

            fn x() -> S {

            }
        }
    """)

    fun `test import inside nested pub module`() = checkAutoImportFixByText("""
        mod b {
            pub struct S;
        }
        pub mod c {
            fn x() -> <error descr="Unresolved reference: `S`">S/*caret*/</error> {}
        }
    """, """
        mod b {
            pub struct S;
        }
        pub mod c {
            use b::S;

            fn x() -> S {}
        }
    """)

    fun `test import inside nested module with multiple choice`() = checkAutoImportFixByTextWithMultipleChoice("""
        mod a {
            pub struct S;
        }
        mod b {
            pub struct S;
        }
        mod c {
            fn x() -> <error descr="Unresolved reference: `S`">S/*caret*/</error> {

            }
        }
    """, listOf("a::S", "b::S"), "b::S", """
        mod a {
            pub struct S;
        }
        mod b {
            pub struct S;
        }
        mod c {
            use b::S;

            fn x() -> S {

            }
        }
    """)

    fun `test import with wildcard reexport 1`() = checkAutoImportFixByText("""
        mod c {
            pub use self::a::*;

            mod a {
                pub struct A;
            }
        }

        fn main() {
            let a = <error descr="Unresolved reference: `A`">A/*caret*/</error>;
        }
    """, """
        use c::A;

        mod c {
            pub use self::a::*;

            mod a {
                pub struct A;
            }
        }

        fn main() {
            let a = A;
        }
    """)

    fun `test import with wildcard reexport 2`() = checkAutoImportFixByText("""
        mod c {
            pub use self::a::{{*}};

            mod a {
                pub struct A;
            }
        }

        fn main() {
            let a = <error descr="Unresolved reference: `A`">A/*caret*/</error>;
        }
    """, """
        use c::A;

        mod c {
            pub use self::a::{{*}};

            mod a {
                pub struct A;
            }
        }

        fn main() {
            let a = A;
        }
    """)

    fun `test group imports`() = checkAutoImportFixByText("""
        // comment
        use foo::Foo;

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
        }
    """, """
        // comment
        use foo::{Bar, Foo};

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = Bar/*caret*/;
        }
    """)

    fun `test add import to existing group`() = checkAutoImportFixByText("""
        // comment
        use foo::{Bar, Foo};

        mod foo {
            pub struct Foo;
            pub struct Bar;
            pub struct Baz;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Baz`">Baz/*caret*/</error>;
        }
    """, """
        // comment
        use foo::{Bar, Baz, Foo};

        mod foo {
            pub struct Foo;
            pub struct Bar;
            pub struct Baz;
        }

        fn main() {
            let f = Baz/*caret*/;
        }
    """)

    fun `test group imports only at the last level`() = checkAutoImportFixByText("""
        use foo::Foo;

        mod foo {
            pub struct Foo;
            pub mod bar {
                pub struct Bar;
            }
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
        }
    """, """
        use foo::bar::Bar;
        use foo::Foo;

        mod foo {
            pub struct Foo;
            pub mod bar {
                pub struct Bar;
            }
        }

        fn main() {
            let f = Bar/*caret*/;
        }
    """)

    fun `test group imports only if the other import doesn't have a modifier`() = checkAutoImportFixByText("""
        pub use foo::Foo;

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
        }
    """, """
        use foo::Bar;
        pub use foo::Foo;

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = Bar/*caret*/;
        }
    """)

    fun `test group imports only if the other import doesn't have an attribute`() = checkAutoImportFixByText("""
        #[attribute = "value"]
        use foo::Foo;

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
        }
    """, """
        use foo::Bar;
        #[attribute = "value"]
        use foo::Foo;

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = Bar/*caret*/;
        }
    """)

    fun `test group imports with aliases 1`() = checkAutoImportFixByText("""
        use foo::Foo as F;

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
        }
    """, """
        use foo::{Bar, Foo as F};

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = Bar/*caret*/;
        }
    """)

    fun `test group imports with aliases 2`() = checkAutoImportFixByText("""
        use foo::{Bar as B, Foo as F};

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>;
        }
    """, """
        use foo::{Bar as B, Bar, Foo as F};

        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        fn main() {
            let f = Bar/*caret*/;
        }
    """)

    fun `test group imports with nested groups`() = checkAutoImportFixByText("""
        use foo::{{Bar, Foo}};

        mod foo {
            pub struct Foo;
            pub struct Bar;
            pub struct Baz;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Baz`">Baz/*caret*/</error>;
        }
    """, """
        use foo::{{Bar, Foo}, Baz};

        mod foo {
            pub struct Foo;
            pub struct Bar;
            pub struct Baz;
        }

        fn main() {
            let f = Baz/*caret*/;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test insert import at correct location`() = checkAutoImportFixByText("""
        use crate::aaa::A;
        use crate::bbb::B;
        use crate::ddd::D;

        pub mod aaa { pub struct A; }
        pub mod bbb { pub struct B; }
        pub mod ccc { pub struct C; }
        pub mod ddd { pub struct D; }

        fn main() {
            let _ = <error descr="Unresolved reference: `C`">C/*caret*/</error>;
        }
    """, """
        use crate::aaa::A;
        use crate::bbb::B;
        use crate::ccc::C;
        use crate::ddd::D;

        pub mod aaa { pub struct A; }
        pub mod bbb { pub struct B; }
        pub mod ccc { pub struct C; }
        pub mod ddd { pub struct D; }

        fn main() {
            let _ = C/*caret*/;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test insert import at correct location (unsorted imports)`() = checkAutoImportFixByText("""
        use crate::aaa::A;
        use crate::ddd::D;
        use crate::bbb::B;

        pub mod aaa { pub struct A; }
        pub mod bbb { pub struct B; }
        pub mod ccc { pub struct C; }
        pub mod ddd { pub struct D; }

        fn main() {
            let _ = <error descr="Unresolved reference: `C`">C/*caret*/</error>;
        }
    """, """
        use crate::aaa::A;
        use crate::ddd::D;
        use crate::bbb::B;
        use crate::ccc::C;

        pub mod aaa { pub struct A; }
        pub mod bbb { pub struct B; }
        pub mod ccc { pub struct C; }
        pub mod ddd { pub struct D; }

        fn main() {
            let _ = C/*caret*/;
        }
    """, checkOptimizeImports = false)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test insert import from different crate at correct location`() = checkAutoImportFixByFileTree("""
    //- dep-lib/lib.rs
        pub mod aaa { pub struct A; }
        pub mod bbb { pub struct B; }
        pub mod ccc { pub struct C; }
    //- main.rs
        use dep_lib_target::aaa::A;
        use dep_lib_target::ccc::C;

        fn main() {
            let _ = <error descr="Unresolved reference: `B`">B/*caret*/</error>;
        }
    """, """
    //- dep-lib/lib.rs
        pub mod aaa { pub struct A; }
        pub mod bbb { pub struct B; }
        pub mod ccc { pub struct C; }
    //- main.rs
        use dep_lib_target::aaa::A;
        use dep_lib_target::bbb::B;
        use dep_lib_target::ccc::C;

        fn main() {
            let _ = B/*caret*/;
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test import outer item in doctest injection`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        /// ```
        /// foo/*caret*/();
        /// ```
        pub fn foo() {}
    """, """
    //- lib.rs
        /// ```
        /// use test_package::foo;
        /// foo();
        /// ```
        pub fn foo() {}
    """, Testmarks.doctestInjectionImport)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test import outer item in doctest injection with tildes`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        /// ~~~
        /// foo/*caret*/();
        /// ~~~
        pub fn foo() {}
    """, """
    //- lib.rs
        /// ~~~
        /// use test_package::foo;
        /// foo();
        /// ~~~
        pub fn foo() {}
    """, Testmarks.doctestInjectionImport)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test import outer item in doctest injection in star comment`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        /**
         * ```
         * foo/*caret*/();
         * ```
         */
        pub fn foo() {}
    """, """
    //- lib.rs
        /**
         * ```
         * use test_package::foo;
         * foo();
         * ```
         */
        pub fn foo() {}
    """,  Testmarks.doctestInjectionImport)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test import second outer item in doctest injection`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        /// ```
        /// use test_package::foo;
        /// foo();
        /// bar/*caret*/();
        /// ```
        pub fn foo() {}
        pub fn bar() {}
    """, """
    //- lib.rs
        /// ```
        /// use test_package::{bar, foo};
        /// foo();
        /// bar();
        /// ```
        pub fn foo() {}
        pub fn bar() {}
    """, Testmarks.doctestInjectionImport)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test import second outer item without grouping in doctest injection`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        /// ```
        /// use test_package::foo;
        /// foo();
        /// baz/*caret*/();
        /// ```
        pub fn foo() {}
        pub mod bar { pub fn baz() {} }
    """, """
    //- lib.rs
        /// ```
        /// use test_package::bar::baz;
        /// use test_package::foo;
        /// foo();
        /// baz();
        /// ```
        pub fn foo() {}
        pub mod bar { pub fn baz() {} }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test import outer item in doctest injection with inner module`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        /// ```
        /// mod bar {
        ///     fn baz() {
        ///         foo/*caret*/();
        ///     }
        /// }
        /// ```
        pub fn foo() {}
    """, """
    //- lib.rs
        /// ```
        /// mod bar {
        ///     use test_package::foo;
        /// fn baz() {
        ///         foo();
        ///     }
        /// }
        /// ```
        pub fn foo() {}
    """, Testmarks.doctestInjectionImport)

    @ExpandMacros
    fun `test import struct from macro`() = checkAutoImportFixByText("""
        mod foo {
            macro_rules! foo {
                () => { pub struct Foo; };
            }
            foo!();
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use foo::Foo;

        mod foo {
            macro_rules! foo {
                () => { pub struct Foo; };
            }
            foo!();
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test import trait for bound (type-related path)`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo { fn foo(&self) {} }
        }
        struct S<T>(T);
        fn foo<T>(a: S<T>) where S<T>: foo::Foo {
            <S<T>>::<error descr="Unresolved reference: `foo`">foo/*caret*/</error>(&a);
        }
    """, """
        use foo::Foo;

        mod foo {
            pub trait Foo { fn foo(&self) {} }
        }
        struct S<T>(T);
        fn foo<T>(a: S<T>) where S<T>: foo::Foo {
            <S<T>>::foo/*caret*/(&a);
        }
    """)

    fun `test import trait for bound (method call)`() = checkAutoImportFixByText("""
        mod foo {
            pub trait Foo { fn foo(&self) {} }
        }
        struct S<T>(T);
        fn foo<T>(a: S<T>) where S<T>: foo::Foo {
            a.<error descr="Unresolved reference: `foo`">foo/*caret*/</error>();
        }
    """, """
        use foo::Foo;

        mod foo {
            pub trait Foo { fn foo(&self) {} }
        }
        struct S<T>(T);
        fn foo<T>(a: S<T>) where S<T>: foo::Foo {
            a.foo();
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2015)
    fun `test import item from a renamed crate (2015 edition)`() = checkAutoImportFixByFileTree("""
        //- dep-lib-to-be-renamed/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """, """
        //- main.rs
        extern crate dep_lib_renamed;

        use dep_lib_renamed::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import item from a renamed crate (2018 edition)`() = checkAutoImportFixByFileTree("""
        //- dep-lib-to-be-renamed/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """, """
        //- main.rs
        use dep_lib_renamed::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)

    fun `test import 'pub(crate)' from the same crate`() = checkAutoImportFixByText("""
        mod foo {
            pub(crate) struct Foo;
        }

        fn main() {
            let f = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        use foo::Foo;

        mod foo {
            pub(crate) struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not try to import 'pub(crate)' item from dependency crate`() = checkAutoImportFixIsUnavailableByFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub(crate) struct Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not try to import an item from 'pub(crate)' mod in dependency crate`() = checkAutoImportFixIsUnavailableByFileTree("""
        //- dep-lib/lib.rs
        pub(crate) mod foo {
            pub struct Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not try to import an item reexported from 'pub(crate)' mod in dependency crate`() = checkAutoImportFixIsUnavailableByFileTree("""
        //- dep-lib/lib.rs
        mod foo {
            pub struct Bar;
        }
        pub(crate) mod baz {
            pub use crate::foo::Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not try to import 'pub(crate)' item reexported from dependency crate`() = checkAutoImportFixIsUnavailableByFileTree("""
        //- dep-lib/lib.rs
        mod foo {
            pub(crate) struct Bar;
        }
        pub mod baz {
            pub use crate::foo::Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not try to import an item reexported by 'pub(crate) use' in dependency crate`() = checkAutoImportFixIsUnavailableByFileTree("""
        //- dep-lib/lib.rs
        mod foo {
            pub struct Bar;
        }
        pub mod baz {
            pub(crate) use crate::foo::Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not try to import an item reexported by intermediate 'pub(crate) use' in dependency crate`() = checkAutoImportFixIsUnavailableByFileTree("""
        //- dep-lib/lib.rs
        mod foo {
            pub struct Bar;
        }
        mod baz {
            pub(crate) use crate::foo::Bar;
        }
        pub mod quux {
            pub use crate::baz::Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not try to import an item reexported by 'pub(crate) extern crate' in dependency crate`() = checkAutoImportFixIsUnavailableByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;
        //- dep-lib/lib.rs
        pub(crate) extern crate trans_lib;
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not try to import item from transitive dependency`() = checkAutoImportFixIsUnavailableByFileTree("""
    //- trans-lib/lib.rs
        pub mod mod1 {
            pub struct Foo;
        }
    //- dep-lib/lib.rs
    //- lib.rs
        fn main() {
            let _ = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import item from transitive dependency reexported by dependency`() = checkAutoImportFixByFileTree("""
    //- trans-lib/lib.rs
        pub mod mod1 {
            pub struct Foo;
        }
    //- dep-lib/lib.rs
        pub use trans_lib::mod1;
    //- lib.rs
        fn main() {
            let _ = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
    //- trans-lib/lib.rs
        pub mod mod1 {
            pub struct Foo;
        }
    //- dep-lib/lib.rs
        pub use trans_lib::mod1;
    //- lib.rs
        use dep_lib_target::mod1::Foo;

        fn main() {
            let _ = Foo;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import macro`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        #[macro_export]
        macro_rules! foo { () => {} }
    //- main.rs
        fn main() {
            foo/*caret*/!();
        }
    """, """
    //- lib.rs
        #[macro_export]
        macro_rules! foo { () => {} }
    //- main.rs
        use test_package::foo;

        fn main() {
            foo!();
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import macro 2`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        pub macro foo() {}
    //- main.rs
        fn main() {
            foo/*caret*/!();
        }
    """, """
    //- lib.rs
        pub macro foo() {}
    //- main.rs
        use test_package::foo;

        fn main() {
            foo!();
        }
    """)

    // e.g. `lazy_static`
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import macro with same name as dependency`() = checkAutoImportFixByFileTreeWithoutHighlighting("""
    //- lib.rs
        #[macro_export]
        macro_rules! test_package { () => {} }
    //- main.rs
        fn main() {
            test_package/*caret*/!();
        }
    """, """
    //- lib.rs
        #[macro_export]
        macro_rules! test_package { () => {} }
    //- main.rs
        use test_package::test_package;

        fn main() {
            test_package!();
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not import function as macro path`() = checkAutoImportFixIsUnavailableByFileTree("""
    //- lib.rs
        pub fn func() {}
    //- main.rs
        fn main() {
            <error descr="Unresolved reference: `func`">func/*caret*/</error>!();
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test do not import cfg-disabled item`() = checkAutoImportFixIsUnavailableByFileTree("""
    //- foo.rs
        pub fn func() {}
    //- main.rs
        #[cfg(not(intellij_rust))]
        mod foo;
        fn main() {
            <error descr="Unresolved reference: `func`">func/*caret*/</error>!();
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test use absolute path when extern crate has same name as child mod`() = checkAutoImportFixByFileTree("""
    //- lib.rs
        pub fn func() {}
    //- main.rs
        mod test_package {}
        fn main() {
            <error descr="Unresolved reference: `func`">func/*caret*/</error>();
        }
    """, """
    //- main.rs
        use ::test_package::func;

        mod test_package {}
        fn main() {
            func();
        }
    """)
}
