/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.utils.import.Testmarks

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class AutoImportFixStdTest : AutoImportFixTestBase() {
    @CheckTestmarkHit(Testmarks.AutoInjectedStdCrate::class)
    fun `test import item from std crate`() = checkAutoImportFixByText("""
        fn foo<T: <error descr="Unresolved reference: `error`">error/*caret*/</error>::Error>(_: T) {}
    """, """
        use std::error;

        fn foo<T: error/*caret*/::Error>(_: T) {}
    """)

    fun `test import item from not std crate`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """, """
        //- main.rs
        use dep_lib_target::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)

    fun `test don't insert extern crate item it is already exists`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        extern crate dep_lib_target;

        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """, """
        //- main.rs
        extern crate dep_lib_target;

        use dep_lib_target::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)

    @MockEdition(Edition.EDITION_2015)
    fun `test insert new extern crate item after existing extern crate items`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        extern crate std;

        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """, """
        //- main.rs
        extern crate std;
        extern crate dep_lib_target;

        use dep_lib_target::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """, checkOptimizeImports = false)

    @MockEdition(Edition.EDITION_2015)
    fun `test insert extern crate item after inner attributes`() = checkAutoImportFixByFileTree("""
        //- main.rs
        #![allow(non_snake_case)]

        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}

        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
    """, """
        //- main.rs
        #![allow(non_snake_case)]

        extern crate dep_lib_target;

        use dep_lib_target::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)

    @CheckTestmarkHit(Testmarks.AutoInjectedStdCrate::class)
    fun `test import reexported item from stdlib`() = checkAutoImportFixByText("""
        fn main() {
            let mutex = <error descr="Unresolved reference: `Mutex`">Mutex/*caret*/</error>::new(Vec::new());
        }
    """, """
        use std::sync::Mutex;

        fn main() {
            let mutex = Mutex/*caret*/::new(Vec::new());
        }
    """)

    fun `test module reexport`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            mod bar {
                pub mod baz {
                    pub struct FooBar;
                }
            }

            pub use self::bar::baz;
        }

        //- main.rs
        fn main() {
            let x = <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>;
        }
    """, """
        //- main.rs
        use dep_lib_target::foo::baz::FooBar;

        fn main() {
            let x = FooBar/*caret*/;
        }
    """)

    @CheckTestmarkHit(Testmarks.AutoInjectedStdCrate::class)
    fun `test module reexport in stdlib`() = checkAutoImportFixByText("""
        fn foo<T: <error descr="Unresolved reference: `Hash`">Hash/*caret*/</error>>(t: T) {}
    """, """
        use std::hash::Hash;

        fn foo<T: Hash/*caret*/>(t: T) {}
    """)

    @CheckTestmarkHit(Testmarks.AutoInjectedCoreCrate::class)
    fun `test import without std crate 1`() = checkAutoImportFixByText("""
        #![no_std]

        fn foo<T: <error descr="Unresolved reference: `Hash`">Hash/*caret*/</error>>(t: T) {}
    """, """
        #![no_std]

        use core::hash::Hash;

        fn foo<T: Hash/*caret*/>(t: T) {}
    """)

    fun `test import without std crate 2`() = checkAutoImportFixByText("""
        #![no_std]

        fn main() {
            let x = <error descr="Unresolved reference: `Rc`">Rc/*caret*/</error>::new(123);
        }
    """, """
        #![no_std]

        extern crate alloc;

        use alloc::rc::Rc;

        fn main() {
            let x = Rc/*caret*/::new(123);
        }
    """)

    fun `test do not insert extern crate item`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;

        //- main.rs
        extern crate dep_lib_target;

        mod bar;
        fn main() {}

        //- bar.rs
        fn bar() {
            let x = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        //- main.rs
        extern crate dep_lib_target;

        mod bar;
        fn main() {}

        //- bar.rs
        use dep_lib_target::Foo;

        fn bar() {
            let x = Foo/*caret*/;
        }
    """)

    @MockEdition(Edition.EDITION_2015)
    fun `test insert extern crate item in crate root`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;

        //- main.rs
        mod bar;
        fn main() {}

        //- bar.rs
        fn bar() {
            let x = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        //- main.rs
        extern crate dep_lib_target;

        mod bar;
        fn main() {}

        //- bar.rs
        use dep_lib_target::Foo;

        fn bar() {
            let x = Foo/*caret*/;
        }
    """)

    // existing `extern crate` is ignored for simplicity
    @MockEdition(Edition.EDITION_2015)
    fun `test insert relative use item 1`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;

        //- main.rs
        mod bar;
        fn main() {}

        //- bar.rs
        extern crate dep_lib_target;

        fn bar() {
            let x = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        //- dep-lib/lib.rs
        pub struct Foo;

        //- main.rs
        extern crate dep_lib_target;

        mod bar;
        fn main() {}

        //- bar.rs
        extern crate dep_lib_target;

        use dep_lib_target::Foo;

        fn bar() {
            let x = Foo/*caret*/;
        }
    """)

    // existing `extern crate` is ignored for simplicity
    @MockEdition(Edition.EDITION_2015)
    fun `test insert relative use item 2`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;

        //- main.rs
        mod bar;
        fn main() {}

        //- bar/mod.rs
        extern crate dep_lib_target;

        mod baz;

        //- bar/baz.rs
        fn baz() {
            let x = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        //- dep-lib/lib.rs
        pub struct Foo;

        //- main.rs
        extern crate dep_lib_target;

        mod bar;
        fn main() {}

        //- bar/mod.rs
        extern crate dep_lib_target;

        mod baz;

        //- bar/baz.rs
        use dep_lib_target::Foo;

        fn baz() {
            let x = Foo/*caret*/;
        }
    """)

    fun `test do not try to highlight primitive types`() = checkAutoImportFixIsUnavailable("""
        pub trait Zero<N> {
            fn zero() -> N;
        }
        impl Zero<f32> for f32 {
            fn zero() -> f32 { 0f32 }
        }

        fn main() {
            let x = f32/*caret*/::zero();
        }
    """)

    fun `test extern crate alias`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;

        //- main.rs
        extern crate dep_lib_target as dep_lib;

        fn main() {
            let foo = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        //- dep-lib/lib.rs
        pub struct Foo;

        //- main.rs
        extern crate dep_lib_target as dep_lib;

        use dep_lib::Foo;

        fn main() {
            let foo = Foo/*caret*/;
        }
    """)

    fun `test extern crate trait method reference`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub trait Foo {
            fn foo(&self);
        }

        impl<T> Foo for T {
            fn foo(&self) {
                unimplemented!()
            }
        }

        //- main.rs
        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo/*caret*/</error>();
        }
    """, """
        //- main.rs
        use dep_lib_target::Foo;

        fn main() {
            let x = 123.foo/*caret*/();
        }
    """)

    fun `test std trait method reference`() = checkAutoImportFixByText("""
        use std::fs::File;

        fn main() {
            let mut s = String::new();
            let mut f = File::open("somefile").unwrap();
            f.<error descr="Unresolved reference: `read_to_string`">read_to_string/*caret*/</error>(&mut s);
        }
    """, """
        use std::fs::File;
        use std::io::Read;

        fn main() {
            let mut s = String::new();
            let mut f = File::open("somefile").unwrap();
            f.read_to_string/*caret*/(&mut s);
        }
    """)

    fun `test std trait method reexported from core`() = checkAutoImportFixByText("""
        #[derive(Debug)]
        struct S;

        fn main() {
            S.<error descr="Unresolved reference: `fmt`">fmt/*caret*/</error>(unreachable!());
        }
    """, """
        use std::fmt::Debug;

        #[derive(Debug)]
        struct S;

        fn main() {
            S.fmt/*caret*/(unreachable!());
        }
    """)

    fun `test do not suggest items from transitive dependencies`() = checkAutoImportFixByFileTree("""
        //- dep-lib-new/lib.rs
        pub struct Foo;

        //- dep-lib/lib.rs
        pub use dep_lib_target::Foo;

        //- main.rs
        fn main() {
            let foo = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        //- main.rs
        use dep_lib_target::Foo;

        fn main() {
            let foo = Foo/*caret*/;
        }
    """)

    fun `test import item from not std crate (edition 2018)`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """, """
        //- main.rs
        use dep_lib_target::foo::Bar;

        fn foo(t: Bar/*caret*/) {}
    """)

    fun `test import HashMap`() = checkAutoImportFixByText("""
        fn main() {
            let a = <error descr="Unresolved reference: `HashMap`">HashMap</error>/*caret*/::new();
        }
    """, """
        use std::collections::HashMap;

        fn main() {
            let a = HashMap/*caret*/::new();
        }
    """)

    fun `test import derived trait method UFCS`() = checkAutoImportFixByText("""
        #[derive(Debug)]
        pub struct S;
        fn main() {
            S::<error descr="Unresolved reference: `fmt`">fmt/*caret*/</error>(&S, panic!(""));
        }
    """, """
        use std::fmt::Debug;

        #[derive(Debug)]
        pub struct S;
        fn main() {
            S::fmt/*caret*/(&S, panic!(""));
        }
    """)

    fun `test pub extern crate reexport`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;
        //- dep-lib/lib.rs
        pub extern crate trans_lib;
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        extern crate dep_lib_target;

        use dep_lib_target::trans_lib::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test pub extern crate reexport in inner module`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;
        //- dep-lib/lib.rs
        pub mod foo {
            pub extern crate trans_lib;
        }
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        extern crate dep_lib_target;

        use dep_lib_target::foo::trans_lib::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test pub extern crate reexport in inner module with reexport`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;
        //- dep-lib/lib.rs
        pub mod foo {
            mod bar {
                pub extern crate trans_lib;
            }

            pub use self::bar::*;
        }
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        extern crate dep_lib_target;

        use dep_lib_target::foo::trans_lib::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test several pub extern crate reexports`() = checkAutoImportFixByFileTree("""
        //- trans-lib-2/lib.rs
        pub struct FooBar;
        //- trans-lib/lib.rs
        pub extern crate trans_lib_2;
        //- dep-lib/lib.rs
        pub extern crate trans_lib;
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        extern crate dep_lib_target;

        use dep_lib_target::trans_lib::trans_lib_2::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test pub extern crate reexport with alias`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;
        //- dep-lib/lib.rs
        pub extern crate trans_lib as transitive;
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        extern crate dep_lib_target;

        use dep_lib_target::transitive::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test pub extern crate reexport with alias in inner module`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;
        //- dep-lib/lib.rs
        pub mod foo {
            pub extern crate trans_lib as transitive;
        }
        //- lib.rs
        extern crate dep_lib_target;

        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        extern crate dep_lib_target;

        use dep_lib_target::foo::transitive::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test import struct when accessing derived method UFCS`() = checkAutoImportFixByText("""
        mod foo {
            #[derive(Debug)]
            pub struct Foo;
        }

        fn main() {
            <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>::fmt();
        }
    """, """
        use crate::foo::Foo;

        mod foo {
            #[derive(Debug)]
            pub struct Foo;
        }

        fn main() {
            Foo/*caret*/::fmt();
        }
    """)

    fun `test module re-export with module alias 1`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub mod foo {
            pub struct FooBar;
        }
        //- dep-lib/lib.rs
        pub use trans_lib::foo as bar;
        //- lib.rs
        fn foo(x: <error descr="Unresolved reference: `bar`">bar/*caret*/</error>::FooBar) {}
    """, """
        //- lib.rs
        use dep_lib_target::bar;

        fn foo(x: bar/*caret*/::FooBar) {}
    """)

    fun `test module re-export with module alias 2`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub mod foo {
            pub struct FooBar;
        }
        //- dep-lib/lib.rs
        pub use trans_lib::foo as bar;
        //- lib.rs
        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        use dep_lib_target::bar::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test crate re-export in use item`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;

        //- dep-lib/lib.rs
        pub use trans_lib;
        //- lib.rs
        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        use dep_lib_target::trans_lib::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test crate re-export in use item with alias`() = checkAutoImportFixByFileTree("""
        //- trans-lib/lib.rs
        pub struct FooBar;

        //- dep-lib/lib.rs
        pub use trans_lib as foo;
        //- lib.rs
        fn foo(x: <error descr="Unresolved reference: `FooBar`">FooBar/*caret*/</error>) {}
    """, """
        //- lib.rs
        use dep_lib_target::foo::FooBar;

        fn foo(x: FooBar/*caret*/) {}
    """)

    fun `test import item from workspace over std and extern crate`() = checkAutoImportFixByFileTreeWithMultipleChoice("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Rc<T> {
                v: T
            }
        }

        //- main.rs
        mod bar {
            pub struct Rc<T> {
                v: T
            }
        }

        fn foo(t: <error descr="Unresolved reference: `Rc`">Rc/*caret*/</error><usize>) {}
    """, listOf("crate::bar::Rc", "std::rc::Rc", "dep_lib_target::foo::Rc"),
        "crate::bar::Rc","""
        //- main.rs
        use crate::bar::Rc;

        mod bar {
            pub struct Rc<T> {
                v: T
            }
        }

        fn foo(t: Rc/*caret*/<usize>) {}
    """)

    fun `test import item from std over extern crate`() = checkAutoImportFixByFileTreeWithMultipleChoice("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Rc<T> {
                v: T
            }
        }

        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Rc`">Rc/*caret*/</error><usize>) {}
    """, listOf("std::rc::Rc", "dep_lib_target::foo::Rc"),
        "std::rc::Rc","""
        //- main.rs
        use std::rc::Rc;

        fn foo(t: Rc/*caret*/<usize>) {}
    """)

    fun `test import item from proc_macro`() = checkAutoImportFixByFileTree("""
    //- dep-proc-macro/lib.rs
        fn foo(_: <error descr="Unresolved reference: `TokenStream`">TokenStream/*caret*/</error>) {}
    """, """
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        fn foo(_: TokenStream) {}
    """)

    fun `test item in core reexported in std`() = checkAutoImportFixByTextWithoutHighlighting("""
        fn main() {
            UnsafeCell/*caret*/;
        }
    """, """
        use std::cell::UnsafeCell;

        fn main() {
            UnsafeCell;
        }
    """)

    @MinRustcVersion("1.51.0")
    fun `test item in core not reexported in std (with no_std)`() = checkAutoImportVariantsByText("""
        #![no_std]
        fn main() {
            SplitInclusive/*caret*/;
        }
    """, listOf("core::slice::SplitInclusive", "core::str::SplitInclusive"))

    fun `test item from 'test' crate (without extern crate)`() = checkAutoImportFixIsUnavailable("""
        fn main() {
            <error descr="Unresolved reference: `test_main`">/*caret*/test_main</error>();
        }
    """)

    fun `test item from 'test' crate (with extern crate)`() = checkAutoImportFixByTextWithoutHighlighting("""
        extern crate test;
        fn main() {
            /*caret*/test_main();
        }
    """, """
        extern crate test;

        use test::test_main;

        fn main() {
            test_main();
        }
    """)

    fun `test add extern crate for alloc (with no_std)`() = checkAutoImportFixByTextWithoutHighlighting("""
        #![no_std]
        fn main() {
            Vec/*caret*/::new();
        }
    """, """
        #![no_std]
        extern crate alloc;

        use alloc::vec::Vec;

        fn main() {
            Vec::new();
        }
    """)

    fun `test don't import method of Borrow trait`() = checkAutoImportFixIsUnavailable("""
        use std::cell::RefCell;
        use std::rc::Rc;

        fn main() {
            let rc = Rc::new(RefCell::new(0));
            let option = Some(rc);
            option.<error>borrow/*caret*/</error>()
        }
    """)

    fun `test import Borrow trait`() = checkAutoImportFixByTextWithoutHighlighting("""
        fn func(_: impl /*caret*/Borrow<i32>) {}
    """, """
        use std::borrow::Borrow;

        fn func(_: impl Borrow<i32>) {}
    """)
}
