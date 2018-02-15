/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions.import

class ImportNameIntentionTest : ImportNameIntentionTestBase() {

    fun `test import struct`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
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

    fun `test import enum variant`() = doAvailableTest("""
        mod foo {
            pub enum Foo { A }
        }

        fn main() {
            Foo::A/*caret*/;
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

    fun `test import function`() = doAvailableTest("""
        mod foo {
            pub fn bar() -> i32 { unimplemented!() }
        }

        fn main() {
            let f = bar/*caret*/();
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

    fun `test import function method`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn foo() {}
            }
        }

        fn main() {
            Foo::foo/*caret*/();
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

    fun `test import module`() = doAvailableTest("""
        mod foo {
            pub mod bar {
                pub fn foo_bar() -> i32 { unimplemented!() }
            }
        }

        fn main() {
            let f = bar/*caret*/::foo_bar();
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

    fun `test insert use item after existing use items`() = doAvailableTest("""
        mod foo {
            pub struct Foo;
            pub struct Bar;
        }

        use foo::Bar;

        fn main() {
            let f = Foo/*caret*/;
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

    fun `test import item from nested module`() = doAvailableTest("""
        mod foo {
            pub mod bar {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
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

    fun `test don't try to import private item`() = doUnavailableTest("""
        mod foo {
            struct Foo;
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test don't try to import from private mod`() = doUnavailableTest("""
        mod foo {
            mod bar {
                pub struct Foo;
            }
        }

        fn main() {
            let f = Foo/*caret*/;
        }
    """)

    fun `test complex module structure`() = doAvailableTest("""
        mod aaa {
            mod bbb {
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

    fun `test complex module structure with file modules`() = doAvailableTestWithFileTree("""
        //- aaa/mod.rs
        mod bbb;
        //- aaa/bbb/mod.rs
        fn foo() {
            let x = Foo/*caret*/;
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
        use ccc::ddd::Foo;

        fn foo() {
            let x = Foo/*caret*/;
        }
    """)

    fun `test import module declared via module declaration`() = doAvailableTestWithFileTree("""
        //- foo/bar.rs
        fn foo_bar() {}
        //- main.rs
        mod foo {
            pub mod bar;
        }
        fn main() {
            bar::foo_bar/*caret*/();
        }
    """, """
        use foo::bar;

        mod foo {
            pub mod bar;
        }
        fn main() {
            bar::foo_bar/*caret*/();
        }
    """)

    fun `test filter import candidates 1`() = doAvailableTest("""
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

    fun `test filter import candidates 2`() = doAvailableTest("""
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

    fun `test filter members without owner prefix`() = doUnavailableTest("""
        mod foo {
            pub struct Foo;
            impl Foo {
                pub fn foo() {}
            }
        }

        fn main() {
            foo/*caret*/();
        }
    """)

    fun `test don't try to import item if it can't be resolved`() = doUnavailableTest("""
        mod foo {
            pub mod bar {
            }
        }
        fn main() {
            bar::foo_bar/*caret*/();
        }
    """)

    fun `test don't import trait method`() = doUnavailableTest("""
        mod foo {
            pub trait Bar {
                fn bar();
            }
        }
        fn main() {
            Bar::bar/*caret*/();
        }
    """)

    fun `test don't import trait const`() = doUnavailableTest("""
        mod foo {
            pub trait Bar {
                const BAR: i32;
            }
        }
        fn main() {
            Bar::BAR/*caret*/();
        }
    """)

    fun `test import reexported item`() = doAvailableTest("""
        mod foo {
            mod bar {
                pub struct Bar;
            }

            pub use self::bar::Bar;
        }

        fn main() {
            Bar/*caret*/;
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

    fun `test import reexported item with alias`() = doAvailableTest("""
        mod foo {
            mod bar {
                pub struct Bar;
            }

            pub use self::bar::Bar as Foo;
        }

        fn main() {
            Foo/*caret*/;
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

    fun `test import reexported item via use group`() = doAvailableTest("""
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

    fun `test import reexported item via 'self'`() = doAvailableTest("""
        mod foo {
            mod bar {
                pub struct Baz;
            }

            pub use self::bar::Baz::{self};
        }

        fn main() {
            let a = Baz/*caret*/;
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

    fun `test import reexported item with complex reexport`() = doAvailableTest("""
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

    fun `test module reexport`() = doAvailableTest("""
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

    fun `test multiple import`() = doAvailableTestWithMultipleChoice("""
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

    fun `test multiple import with reexports`() = doAvailableTestWithMultipleChoice("""
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

    fun `test double module reexport`() = doAvailableTestWithMultipleChoice("""
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

    fun `test cyclic module reexports`() = doAvailableTestWithMultipleChoice("""
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

    fun `test crazy cyclic module reexports`() = doAvailableTestWithMultipleChoice("""
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
}
