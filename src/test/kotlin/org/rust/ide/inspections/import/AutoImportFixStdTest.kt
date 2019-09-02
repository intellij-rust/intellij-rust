/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.import

import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class AutoImportFixStdTest : AutoImportFixTestBase() {
    fun `test import item from std crate`() = checkAutoImportFixByText("""
        fn foo<T: <error descr="Unresolved reference: `io`">io::Read/*caret*/</error>>(t: T) {}
    """, """
        use std::io;

        fn foo<T: io::Read/*caret*/>(t: T) {}
    """, AutoImportFix.Testmarks.autoInjectedStdCrate)

    fun `test import item from not std crate`() = checkAutoImportFixByFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub struct Bar;
        }
        //- main.rs
        fn foo(t: <error descr="Unresolved reference: `Bar`">Bar/*caret*/</error>) {}
    """, """
        //- main.rs
        extern crate dep_lib_target;

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
    """)

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

    fun `test import reexported item from stdlib`() = checkAutoImportFixByText("""
        fn main() {
            let mutex = <error descr="Unresolved reference: `Mutex`">Mutex/*caret*/::new</error>(Vec::new());
        }
    """, """
        use std::sync::Mutex;

        fn main() {
            let mutex = Mutex/*caret*/::new(Vec::new());
        }
    """, AutoImportFix.Testmarks.autoInjectedStdCrate)

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
        extern crate dep_lib_target;

        use dep_lib_target::foo::baz::FooBar;

        fn main() {
            let x = FooBar/*caret*/;
        }
    """)

    fun `test module reexport in stdlib`() = checkAutoImportFixByText("""
        fn foo<T: <error descr="Unresolved reference: `Hash`">Hash/*caret*/</error>>(t: T) {}
    """, """
        use std::hash::Hash;

        fn foo<T: Hash/*caret*/>(t: T) {}
    """, AutoImportFix.Testmarks.autoInjectedStdCrate)

    fun `test import without std crate 1`() = checkAutoImportFixByText("""
        #![no_std]

        fn foo<T: <error descr="Unresolved reference: `Hash`">Hash/*caret*/</error>>(t: T) {}
    """, """
        #![no_std]

        use core::hash::Hash;

        fn foo<T: Hash/*caret*/>(t: T) {}
    """, AutoImportFix.Testmarks.autoInjectedCoreCrate)

    fun `test import without std crate 2`() = checkAutoImportFixByText("""
        #![no_std]

        fn main() {
            let x = <error descr="Unresolved reference: `Rc`">Rc::new/*caret*/</error>(123);
        }
    """, """
        #![no_std]

        extern crate alloc;

        use alloc::rc::Rc;

        fn main() {
            let x = Rc::new/*caret*/(123);
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
        mod bar;
        fn main() {}

        //- bar.rs
        extern crate dep_lib_target;

        use self::dep_lib_target::Foo;

        fn bar() {
            let x = Foo/*caret*/;
        }
    """, AutoImportFix.Testmarks.externCrateItemInNotCrateRoot)

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
        mod bar;
        fn main() {}

        //- bar/mod.rs
        extern crate dep_lib_target;

        mod baz;

        //- bar/baz.rs
        use super::dep_lib_target::Foo;

        fn baz() {
            let x = Foo/*caret*/;
        }
    """, AutoImportFix.Testmarks.externCrateItemInNotCrateRoot)

    fun `test do not try to highlight primitive types`() = checkAutoImportFixIsUnavailable("""
        pub trait Zero<N> {
            fn zero() -> N;
        }
        impl Zero<f32> for f32 {
            fn zero() -> f32 { 0f32 }
        }

        fn main() {
            let x = f32/*error*/::zero();
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
        extern crate dep_lib_target;

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
        extern crate dep_lib_target;

        pub use dep_lib_target::Foo;

        //- main.rs
        fn main() {
            let foo = <error descr="Unresolved reference: `Foo`">Foo/*caret*/</error>;
        }
    """, """
        //- dep-lib-new/lib.rs
        pub struct Foo;

        //- dep-lib/lib.rs
        extern crate dep_lib_target;

        pub use dep_lib_target::Foo;

        //- main.rs
        extern crate dep_lib_target;

        use dep_lib_target::Foo;

        fn main() {
            let foo = Foo/*caret*/;
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
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
            let a = <error descr="Unresolved reference: `HashMap`">HashMap::new</error>/*caret*/();
        }
    """, """
        use std::collections::HashMap;

        fn main() {
            let a = HashMap::new/*caret*/();
        }
    """)

    fun `test import derived trait method UFCS`() = checkAutoImportFixByText("""
        #[derive(Debug)]
        pub struct S;
        fn main() {
            <error descr="Unresolved reference: `fmt`">S::fmt/*caret*/</error>(&S, panic!(""));
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
            <error descr="Unresolved reference: `Foo`">Foo::fmt/*caret*/</error>();
        }
    """, """
        use foo::Foo;

        mod foo {
            #[derive(Debug)]
            pub struct Foo;
        }
        
        fn main() {
            Foo::fmt/*caret*/();
        }
    """)
}
