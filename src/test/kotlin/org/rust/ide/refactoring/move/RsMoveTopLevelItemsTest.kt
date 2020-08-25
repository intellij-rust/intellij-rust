/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import org.rust.ExpandMacros
import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsMoveTopLevelItemsTest : RsMoveTopLevelItemsTestBase() {

    fun `test simple`() = doTest("""
    //- main.rs
        mod mod1 {
            fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {}
    """, """
    //- main.rs
        mod mod1 {}
        mod mod2 {
            fn foo() {}
        }
    """)

    fun `test outside reference to private item of old mod`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() { bar(); }
            fn bar() {}
        }
        mod mod2/*target*/ {}
    """)

    fun `test outside reference to private method of struct in old mod using UFCS`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() { Bar::bar(); }
            pub struct Bar {}
            impl Bar {
                fn bar() {}  // private
            }
        }
        mod mod2/*target*/ {}
    """)

    fun `test outside reference to public method of struct in old mod using UFCS`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() { Bar::bar(); }
            pub struct Bar {}
            impl Bar {
                pub fn bar() {}  // public
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub struct Bar {}
            impl Bar {
                pub fn bar() {}  // public
            }
        }
        mod mod2 {
            use crate::mod1::Bar;

            fn foo() { Bar::bar(); }
        }
    """)

    fun `test inside reference, when new mod is private`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            fn bar() { foo(); }
            fn foo/*caret*/() {}
        }
        mod inner {
            // private
            mod mod2/*target*/ {}
        }
    """)

    fun `test inside reference from other crate, when new mod is private`() = doTestConflictsError("""
    //- lib.rs
        pub fn foo/*caret*/() {}
        mod mod1/*target*/ {}
    //- main.rs
        fn main() {
            test_package::foo();
        }
    """)

    fun `test inside reference to private field`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/ { field: i32 }
            fn bar(foo: &Foo) { let _ = &foo.field; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to public field`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/ { pub field: i32 }
            fn bar(foo: &Foo) { let _ = &foo.field; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to private field in constructor`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/ { field: i32 }
            fn bar() { let _ = Foo { field: 0 }; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to public field in constructor`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/ { pub field: i32 }
            fn bar() { let _ = Foo { field: 0 }; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to private field in destructuring`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/ { field: i32 }
            fn bar(foo: &Foo) { let Foo { field } = foo; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to public field in destructuring`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/ { pub field: i32 }
            fn bar(foo: &Foo) { let Foo { field } = foo; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to private field in destructuring using type alias`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/ { field: i32 }
            type Bar = Foo;
            fn bar(foo: &Bar) { let Bar { field } = foo; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to public field in destructuring using type alias`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/ { pub field: i32 }
            type Bar = Foo;
            fn bar(foo: &Bar) { let Bar { field } = foo; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to private field of tuple struct in destructuring 1`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/(i32);
            fn bar(foo: &Foo) { let Foo(_) = foo; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to private field of tuple struct in destructuring 2`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/(pub i32, i32);
            fn bar(foo: &Foo) { let Foo(_, ..) = foo; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to public field of tuple struct in destructuring`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub struct Foo/*caret*/(pub i32);
            fn bar(foo: &Foo) { let Foo(_) = foo; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to private method`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            pub struct Foo {}
            impl Foo/*caret*/ {
                fn func(&self) {}
            }
            fn bar(foo: &Foo) { foo.func(); }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to public method`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub struct Foo {}
            impl Foo/*caret*/ {
                pub fn func(&self) {}
            }
            fn bar(foo: &Foo) { foo.func(); }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to private method UFCS`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            pub struct Foo {}
            impl Foo/*caret*/ {
                fn func() {}
            }
            fn bar() { Foo::func(); }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to public method UFCS`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub struct Foo {}
            impl Foo/*caret*/ {
                pub fn func() {}
            }
            fn bar() { Foo::func(); }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to enum variant`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub enum Foo/*caret*/ { Foo1, Foo2 }
            fn bar() { let _ = Foo::Foo1; }
        }
        mod mod2/*target*/ {}
    """)

    fun `test inside reference to trait method`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub trait Foo/*caret*/ {
                fn foo(&self) {}
            }
            impl Foo for ()/*caret*/ {}
            fn bar() { ().foo(); }
        }
        mod mod2/*target*/ {}
    """)

    fun `test self references to private method and fields`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            struct Foo1/*caret*/ { field: i32 }
            fn bar1/*caret*/() {
                let foo1 = Foo1 { field: 0 };
                let _ = foo1.field;
                let Foo { field } = foo1;
            }
            struct Foo2/*caret*/(i32);
            fn bar2/*caret*/() {
                let foo2 = Foo2(0);
                let _ = foo2.field;
                let Foo2(_) = foo2;
            }
            struct Foo3/*caret*/ {}
            impl Foo3/*caret*/ {
                fn func(&self) {}
            }
            fn bar3/*caret*/(foo: &Foo3) {
                Foo3::func(foo);
                foo.func();
            }
        }
        mod mod2/*target*/ {}
    """)

    fun `test outside reference to reexported function in private mod`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub use mod1_inner::*;
            mod mod1_inner {
                pub fn bar() {}
            }
            fn foo/*caret*/() { mod1_inner::bar(); }
        }
        mod mod2/*target*/ {}
    """)

    fun `test outside reference to method of reexported struct in private mod`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            pub use mod1_inner::*;
            mod mod1_inner {
                pub struct Bar;
                impl Bar {
                    pub fn bar() {}
                }
            }
            fn foo/*caret*/() { mod1_inner::Bar::bar(); }
        }
        mod mod2/*target*/ {}
    """)

    @ExpandMacros
    fun `test no exception when source file has reference to expanded item`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {}
            fn bar(s: Struct) {
                s.field;
            }

            macro_rules! gen_struct {
                () => { struct Struct { field: i32 } };
            }
            gen_struct!();
        }
        mod mod2/*target*/ {}
    """)

    fun `test absolute outside reference which should be changed because of reexports`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub use bar::*;
            mod mod1 {
                fn foo/*caret*/() { crate::inner1::bar::bar_func(); }
            }
            // private
            mod bar { pub fn bar_func() {} }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod inner1 {
            pub use bar::*;
            mod mod1 {}
            // private
            mod bar { pub fn bar_func() {} }
        }
        mod mod2 {
            fn foo() { crate::inner1::bar_func(); }
        }
    """)

    fun `test outside reference to reexported item`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub use mod1_inner::bar;
            mod mod1_inner {
                pub fn bar() {}
            }
            fn foo/*caret*/() { mod1_inner::bar(); }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub use mod1_inner::bar;
            mod mod1_inner {
                pub fn bar() {}
            }
        }
        mod mod2 {
            use crate::mod1;

            fn foo() { mod1::bar(); }
        }
    """)

    fun `test inside reference, moved item under reexport before move`() = doTest("""
    //- lib.rs
        mod inner {
            pub use mod1::*;
            // private
            mod mod1 {
                pub fn foo/*caret*/() {}
            }
            pub mod mod2/*target*/ {}
        }
        mod usage {
            fn test() { crate::inner::foo(); }
        }
    """, """
    //- lib.rs
        mod inner {
            pub use mod1::*;
            // private
            mod mod1 {}
            pub mod mod2 {
                pub fn foo() {}
            }
        }
        mod usage {
            fn test() { crate::inner::mod2::foo(); }
        }
    """)

    fun `test inside reference, moved item under reexport after move`() = doTest("""
    //- lib.rs
        mod inner {
            pub use mod2::*;
            pub mod mod1 {
                pub fn foo/*caret*/() {}
            }
            // private
            mod mod2/*target*/ {
                pub fn bar() {}
            }
        }
        mod usage {
            fn test() { crate::inner::mod1::foo(); }
        }
    """, """
    //- lib.rs
        mod inner {
            pub use mod2::*;
            pub mod mod1 {}
            // private
            mod mod2 {
                pub fn bar() {}

                pub fn foo() {}
            }
        }
        mod usage {
            fn test() { crate::inner::foo(); }
        }
    """)

    fun `test inside reference, both source and target parent modules under reexport`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub use inner2::*;
            mod inner2 {
                pub mod mod1 {
                    pub fn foo/*caret*/() {}
                }
                pub mod mod2/*target*/ {}
            }
        }
        mod usage {
            fn test() { crate::inner1::mod1::foo(); }
        }
    """, """
    //- lib.rs
        mod inner1 {
            pub use inner2::*;
            mod inner2 {
                pub mod mod1 {}
                pub mod mod2 {
                    pub fn foo() {}
                }
            }
        }
        mod usage {
            fn test() { crate::inner1::mod2::foo(); }
        }
    """)

    fun `test inside reference, grandparent module under reexport`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub use inner2::*;
            mod inner2 {  // private
                pub mod mod1 {
                    pub fn foo/*caret*/() {}
                }
                pub mod mod2/*target*/ {}
            }
        }

        fn test1() { inner1::mod1::foo(); }
        mod usages {
            fn test2() { crate::inner1::mod1::foo(); }
        }
    """, """
    //- lib.rs
        mod inner1 {
            pub use inner2::*;
            mod inner2 {  // private
                pub mod mod1 {}
                pub mod mod2 {
                    pub fn foo() {}
                }
            }
        }

        fn test1() { inner1::mod2::foo(); }
        mod usages {
            fn test2() { crate::inner1::mod2::foo(); }
        }
    """)

    fun `test move to other crate simple`() = doTest("""
    //- main.rs
        mod mod1 {
            fn foo/*caret*/() {}
        }
    //- lib.rs
        mod mod2/*target*/ {}
    """, """
    //- main.rs
        mod mod1 {}
    //- lib.rs
        mod mod2 {
            fn foo() {}
        }
    """)
}
