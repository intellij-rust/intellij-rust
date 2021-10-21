/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ide.inspections.lints.RsUnusedImportInspection

@WithEnabledInspections(RsUnusedImportInspection::class)
@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsMoveTopLevelItemsTest : RsMoveTopLevelItemsTestBase() {

    fun `test simple`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            fn foo() {}
        }
    """)

    fun `test item with same name exists in new mod 1`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {
            fn foo() {}
        }
    """)

    fun `test item with same name exists in new mod 2`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {
            struct foo;
        }
    """)

    fun `test item with same name exists in new mod 3`() = doTestNoConflicts("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {
            struct foo {}
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

    fun `test outside reference to item which has pub(in old_parent_mod) visibility`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() { bar::bar_func(); }
            pub mod bar {
                pub(super) fn bar_func() {}  // private for mod2
            }
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

    fun `test add pub to moved items if necessary`() = doTest("""
    //- lib.rs
        mod mod1 {
            mod foo1/*caret*/ { pub fn foo1_func() {} }
            fn foo2/*caret*/() {}
            struct Foo3/*caret*/ {}
            struct Foo4/*caret*/ { pub field: i32 }
            struct Foo5/*caret*/ { pub field: i32 }
            struct Foo6/*caret*/(pub i32);
            struct Foo7/*caret*/(pub i32);
            fn bar() {
                foo1::foo1_func();
                foo2();
                let _ = Foo3 {};
                let _ = Foo4 { field: 0 };
                let Foo5 { field: _ } = None.unwrap();
                let _ = Foo6(0);
                let Foo7(_) = None.unwrap();
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use crate::mod2;
            use crate::mod2::{foo1, Foo3, Foo4, Foo5, Foo6, Foo7};

            fn bar() {
                foo1::foo1_func();
                mod2::foo2();
                let _ = Foo3 {};
                let _ = Foo4 { field: 0 };
                let Foo5 { field: _ } = None.unwrap();
                let _ = Foo6(0);
                let Foo7(_) = None.unwrap();
            }
        }
        mod mod2 {
            pub mod foo1 { pub fn foo1_func() {} }

            pub fn foo2() {}

            pub struct Foo3 {}

            pub struct Foo4 { pub field: i32 }

            pub struct Foo5 { pub field: i32 }

            pub struct Foo6(pub i32);

            pub struct Foo7(pub i32);
        }
    """)

    fun `test add pub to moved items if necessary when items has doc comments`() = doTest("""
    //- lib.rs
        mod mod1 {
            // comment 1
            #[attr1]
            fn foo1/*caret*/() {}
            /// comment 2
            #[attr2]
            struct Foo2/*caret*/ {}
            fn bar() {
                foo1();
                let _ = Foo2 {};
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use crate::mod2;
            use crate::mod2::Foo2;

            fn bar() {
                mod2::foo1();
                let _ = Foo2 {};
            }
        }
        mod mod2 {
            // comment 1
            #[attr1]
            pub fn foo1() {}

            /// comment 2
            #[attr2]
            pub struct Foo2 {}
        }
    """)

    fun `test add pub to moved items if necessary when moving to other crate`() = doTest("""
    //- main.rs
        mod mod1 {
            fn foo1/*caret*/() {}
            pub(self) fn foo2/*caret*/() {}
            pub(in mod1) fn foo3/*caret*/() {}
            fn bar() {
                foo1();
                foo2();
                foo3();
            }
        }
    //- lib.rs
        /*target*/
    """, """
    //- main.rs
        mod mod1 {
            fn bar() {
                test_package::foo1();
                test_package::foo2();
                test_package::foo3();
            }
        }
    //- lib.rs
        pub fn foo1() {}

        pub fn foo2() {}

        pub fn foo3() {}
    """)

    fun `test change scope for pub(in) visibility`() = doTest("""
    //- lib.rs
        mod inner1 {
            mod inner2 {
                mod inner3 {
                    mod mod1 {
                        pub(crate) fn foo1/*caret*/() {}
                        pub(self) fn foo2/*caret*/() {}
                        pub(super) fn foo3/*caret*/() {}
                        pub(in super::super) fn foo4/*caret*/() {}
                        pub(in crate::inner1) fn foo5/*caret*/() {}
                    }
                }
                mod mod2/*target*/ {}
            }
        }
    """, """
    //- lib.rs
        mod inner1 {
            mod inner2 {
                mod inner3 {
                    mod mod1 {}
                }
                mod mod2 {
                    pub(crate) fn foo1() {}

                    pub(self) fn foo2() {}

                    pub(in crate::inner1::inner2) fn foo3() {}

                    pub(in crate::inner1::inner2) fn foo4() {}

                    pub(in crate::inner1) fn foo5() {}
                }
            }
        }
    """)

    fun `test move inherent impl without struct to different crate 1`() = doTestConflictsError("""
    //- lib.rs
        pub struct Foo {}
        impl Foo/*caret*/ {}
    //- main.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move inherent impl without struct to different crate 2`() = doTestConflictsError("""
    //- lib.rs
        pub mod foo1 {
            pub struct Foo {}
        }
        pub mod foo2/*caret*/ {
            use crate::foo1::Foo;
            impl Foo {}
        }
    //- main.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move inherent impl without struct to same crate`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub struct Foo {}
            impl Foo/*caret*/ {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub struct Foo {}
        }
        mod mod2 {
            use crate::mod1::Foo;

            impl Foo {}
        }
    """)

    fun `test move inherent impl together with struct`() = doTest("""
    //- lib.rs
        mod mod1 {
            struct Foo/*caret*/ {}
            impl/*caret*/ Foo {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            struct Foo {}

            impl Foo {}
        }
    """)

    fun `test move struct without inherent impl to different crate 1`() = doTestConflictsError("""
    //- main.rs
        pub struct Foo/*caret*/ {}
        impl Foo {}
    //- lib.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move struct without inherent impl to different crate 2`() = doTestConflictsError("""
    //- main.rs
        pub mod foo1/*caret*/ {
            pub struct Foo {}
        }
        pub mod foo2 {
            use crate::foo1::Foo;
            impl Foo {}
        }
    //- lib.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move struct without inherent impl to same crate`() = doTest("""
    //- lib.rs
        mod mod1 {
            struct Foo/*caret*/ {}
            impl Foo {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use crate::mod2::Foo;

            impl Foo {}
        }
        mod mod2 {
            pub struct Foo {}
        }
    """)

    fun `test move struct with inherent impl to different crate`() = doTest("""
    //- main.rs
        mod mod1 {
            struct Foo/*caret*/ {}
            impl Foo/*caret*/ {}
        }
    //- lib.rs
        mod mod2/*target*/ {}
    """, """
    //- main.rs
        mod mod1 {}
    //- lib.rs
        mod mod2 {
            struct Foo {}

            impl Foo {}
        }
    """)

    fun `test move trait impl without trait to different crate 1`() = doTestConflictsError("""
    //- lib.rs
        pub trait Foo {}
        impl Foo for ()/*caret*/ {}
    //- main.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move trait impl without trait to different crate 2`() = doTestConflictsError("""
    //- lib.rs
        pub mod mod1 {
            pub trait Foo {}
            impl Foo for Bar/*caret*/ {}
            pub struct Bar {}
        }
    //- main.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move trait impl without trait to different crate 3`() = doTestConflictsError("""
    //- lib.rs
        pub mod mod1 {
            pub trait Foo<T> {}
            impl Foo<Bar2> for Bar1/*caret*/ {}
            pub struct Bar1 {}
            pub struct Bar2 {}
        }
    //- main.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move trait impl with implementing type to different crate 1`() = doTestNoConflicts("""
    //- lib.rs
        pub mod mod1 {
            pub trait Foo {}
            impl Foo for Bar/*caret*/ {}
            pub struct Bar/*caret*/ {}
        }
    //- main.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move trait impl with implementing type to different crate 2`() = doTestNoConflicts("""
    //- lib.rs
        pub mod mod1 {
            pub trait Foo<T> {}
            impl Foo<Bar> for ()/*caret*/ {}
            pub struct Bar/*caret*/ {}
        }
    //- main.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test move trait with trait impl to different crate`() = doTestNoConflicts("""
    //- lib.rs
        pub mod mod1 {
            pub trait Foo/*caret*/ {}
            impl Foo for ()/*caret*/ {}
        }
    //- main.rs
        pub mod mod2/*target*/ {}
    """)

    fun `test spaces 1`() = doTest("""
    //- lib.rs
        mod mod1 {
            const C1/*caret*/: i32 = 0;
            const C2/*caret*/: i32 = 0;
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            const C1: i32 = 0;
            const C2: i32 = 0;
        }
    """)

    fun `test spaces 2`() = doTest("""
    //- lib.rs
        mod mod1 {
            const C1/*caret*/: i32 = 0;
            const C2: i32 = 0;
            const C3/*caret*/: i32 = 0;
            const C4: i32 = 0;
        }
        mod mod2/*target*/ {
            const D1: i32 = 0;
            const D2: i32 = 0;
        }
    """, """
    //- lib.rs
        mod mod1 {
            const C2: i32 = 0;
            const C4: i32 = 0;
        }
        mod mod2 {
            const D1: i32 = 0;
            const D2: i32 = 0;
            const C1: i32 = 0;
            const C3: i32 = 0;
        }
    """)

    // spaces before moved items are moved to new file
    fun `test spaces 3`() = doTest("""
    //- lib.rs
        mod mod1 {
            const C0: i32 = 0;

            const C1/*caret*/: i32 = 0;
            const C2: i32 = 0;


            const C3/*caret*/: i32 = 0;
            const C4: i32 = 0;
            const C5/*caret*/: i32 = 0;
        }
        mod mod2/*target*/ {
            const D1: i32 = 0;
        }
    """, """
    //- lib.rs
        mod mod1 {
            const C0: i32 = 0;
            const C2: i32 = 0;
            const C4: i32 = 0;
        }
        mod mod2 {
            const D1: i32 = 0;

            const C1: i32 = 0;


            const C3: i32 = 0;
            const C5: i32 = 0;
        }
    """)

    // spaces after moved items are kept in old file
    fun `test spaces 4`() = doTest("""
    //- lib.rs
        mod mod1 {
            const C0: i32 = 0;
            const C1/*caret*/: i32 = 0;

            const C2: i32 = 0;
            const C3/*caret*/: i32 = 0;


            const C4: i32 = 0;
            const C5/*caret*/: i32 = 0;
        }
        mod mod2/*target*/ {
            const D1: i32 = 0;
        }
    """, """
    //- lib.rs
        mod mod1 {
            const C0: i32 = 0;

            const C2: i32 = 0;


            const C4: i32 = 0;
        }
        mod mod2 {
            const D1: i32 = 0;
            const C1: i32 = 0;
            const C3: i32 = 0;
            const C5: i32 = 0;
        }
    """)

    // looks like these two tests always pass,
    // regardless of actual behaviour in real IDEA
    fun `test remove double newline at end of target file`() = doTest("""
    //- lib.rs
        mod mod1;
        mod mod2;
    //- mod1.rs
        fn foo/*caret*/() {}

        fn mod1_func() {}
    //- mod2.rs
        /*target*/fn mod2_func() {}
    """, """
    //- lib.rs
        mod mod1;
        mod mod2;
    //- mod1.rs
        fn mod1_func() {}
    //- mod2.rs
        fn mod2_func() {}

        fn foo() {}
    """)

    fun `test remove double newline at end of source file`() = doTest("""
    //- lib.rs
        mod mod1;
        mod mod2;
    //- mod1.rs
        fn mod1_func() {}

        fn foo/*caret*/() {}
    //- mod2.rs
        /*target*/fn mod2_func() {}
    """, """
    //- lib.rs
        mod mod1;
        mod mod2;
    //- mod1.rs
        fn mod1_func() {}
    //- mod2.rs
        fn mod2_func() {}

        fn foo() {}
    """)

    fun `test move doc comments together with items`() = doTest("""
    //- lib.rs
        mod mod1 {
            /// comment 1
            fn foo1/*caret*/() {}
            /// comment 2
            struct Foo2/*caret*/ {}
            /// comment 3
            impl Foo2/*caret*/ {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            /// comment 1
            fn foo1() {}

            /// comment 2
            struct Foo2 {}

            /// comment 3
            impl Foo2 {}
        }
    """)

    fun `test move usual comments together with items`() = doTest("""
    //- lib.rs
        mod mod1 {
            // comment 1
            fn foo1/*caret*/() {}
            // comment 2
            struct Foo2/*caret*/ {}
            // comment 3
            impl Foo2/*caret*/ {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            // comment 1
            fn foo1() {}

            // comment 2
            struct Foo2 {}

            // comment 3
            impl Foo2 {}
        }
    """)

    fun `test copy usual imports from old mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            use crate::bar;
            use crate::bar::BarStruct;
            fn foo/*caret*/() {
                bar::bar_func();
                let _ = BarStruct {};
            }
        }
        mod mod2/*target*/ {}
        mod bar {
            pub fn bar_func() {}
            pub struct BarStruct {}
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            use crate::bar;
            use crate::bar::BarStruct;

            fn foo() {
                bar::bar_func();
                let _ = BarStruct {};
            }
        }
        mod bar {
            pub fn bar_func() {}
            pub struct BarStruct {}
        }
    """)

    // it is idiomatic to import parent mod for functions, and directly import structs/enums
    // https://doc.rust-lang.org/book/ch07-04-bringing-paths-into-scope-with-the-use-keyword.html#creating-idiomatic-use-paths
    fun `test add usual imports for items in old mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            use inner::Bar4;
            fn foo/*caret*/() {
                bar1();
                let _ = Bar2 {};
                inner::bar3();
                let _ = Bar4 {};
            }
            pub mod inner {
                pub fn bar3() {}
                pub struct Bar4 {}
            }
            pub fn bar1() {}
            pub struct Bar2 {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub mod inner {
                pub fn bar3() {}
                pub struct Bar4 {}
            }
            pub fn bar1() {}
            pub struct Bar2 {}
        }
        mod mod2 {
            use crate::mod1;
            use crate::mod1::{Bar2, inner};
            use crate::mod1::inner::Bar4;

            fn foo() {
                mod1::bar1();
                let _ = Bar2 {};
                inner::bar3();
                let _ = Bar4 {};
            }
        }
    """)

    fun `test outside reference to function in old mod when move from crate root`() = doTest("""
    //- lib.rs
        fn foo/*caret*/() { bar(); }
        fn bar() {}
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        fn bar() {}
        mod mod2 {
            fn foo() { crate::bar(); }
        }
    """)


    fun `test copy trait imports from old mod (method call)`() = doTest("""
    //- lib.rs
        mod mod1 {
            use crate::bar::Bar;
            fn foo/*caret*/() { ().bar_func(); }
        }
        mod mod2/*target*/ {}
        mod bar {
            pub trait Bar { fn bar_func(&self) {} }
            impl Bar for () {}
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            use crate::bar::Bar;

            fn foo() { ().bar_func(); }
        }
        mod bar {
            pub trait Bar { fn bar_func(&self) {} }
            impl Bar for () {}
        }
    """)

    fun `test outside reference to trait in old mod (method call)`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() { ().bar(); }
            pub trait Bar { fn bar(&self) {} }
            impl Bar for () {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub trait Bar { fn bar(&self) {} }
            impl Bar for () {}
        }
        mod mod2 {
            use crate::mod1::Bar;

            fn foo() { ().bar(); }
        }
    """)

    fun `test self reference to trait (method call)`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo1/*caret*/() { ().foo(); }
            pub trait Foo2/*caret*/ { fn foo(&self) {} }
            impl Foo2 for ()/*caret*/ {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            fn foo1() { ().foo(); }

            pub trait Foo2 { fn foo(&self) {} }

            impl Foo2 for () {}
        }
    """)

    fun `test inside reference to moved trait (method call)`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub trait Foo/*caret*/ { fn foo(&self) {} }
            impl Foo for ()/*caret*/ {}
            fn bar() { ().foo(); }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use crate::mod2::Foo;

            fn bar() { ().foo(); }
        }
        mod mod2 {
            pub trait Foo { fn foo(&self) {} }

            impl Foo for () {}
        }
    """)


    fun `test copy trait imports from old mod (UFCS)`() = doTest("""
    //- lib.rs
        mod mod1 {
            use crate::foo::{Foo, Trait};
            fn func/*caret*/(foo: &Foo) { Foo::func(foo); }
        }
        mod mod2/*target*/ {}
        mod foo {
            pub struct Foo;
            pub trait Trait { fn func(&self) {} }
            impl Trait for Foo {}
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            use crate::foo::{Foo, Trait};

            fn func(foo: &Foo) { Foo::func(foo); }
        }
        mod foo {
            pub struct Foo;
            pub trait Trait { fn func(&self) {} }
            impl Trait for Foo {}
        }
    """)

    fun `test outside reference to trait in old mod (UFCS)`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub struct Foo;
            pub trait Trait { fn func(&self) {} }
            impl Trait for Foo {}
            fn func/*caret*/(foo: &Foo) { Foo::func(foo); }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub struct Foo;
            pub trait Trait { fn func(&self) {} }
            impl Trait for Foo {}
        }
        mod mod2 {
            use crate::mod1::{Foo, Trait};

            fn func(foo: &Foo) { Foo::func(foo); }
        }
    """)

    fun `test self reference to trait (UFCS)`() = doTest("""
    //- lib.rs
        mod mod1 {
            struct Foo/*caret*/;
            pub trait Trait/*caret*/ { fn func(&self) {} }
            impl Trait for Foo/*caret*/ {}
            fn func/*caret*/(foo: &Foo) { Foo::func(foo); }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            struct Foo;

            pub trait Trait { fn func(&self) {} }

            impl Trait for Foo {}

            fn func(foo: &Foo) { Foo::func(foo); }
        }
    """)

    fun `test inside reference to moved trait (UFCS)`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub trait Trait/*caret*/ { fn func(&self) {} }
            pub struct Foo;
            impl Trait for Foo/*caret*/ {}
            fn func(foo: &Foo) { Foo::func(foo); }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use crate::mod2::Trait;

            pub struct Foo;

            fn func(foo: &Foo) { Foo::func(foo); }
        }
        mod mod2 {
            use crate::mod1::Foo;

            pub trait Trait { fn func(&self) {} }

            impl Trait for Foo {}
        }
    """)


    fun `test copy trait imports from old mod (assoc const)`() = doTest("""
    //- lib.rs
        mod mod1 {
            use crate::foo::{Foo, Trait};
            fn func/*caret*/() {
                let _ = Foo::C;
            }
        }

        mod mod2/*target*/ {}

        mod foo {
            pub trait Trait { const C: i32 = 1; }
            pub struct Foo;
            impl Trait for Foo {}
        }
    """, """
    //- lib.rs
        mod mod1 {}

        mod mod2 {
            use crate::foo::{Foo, Trait};

            fn func() {
                let _ = Foo::C;
            }
        }

        mod foo {
            pub trait Trait { const C: i32 = 1; }
            pub struct Foo;
            impl Trait for Foo {}
        }
    """)

    fun `test self reference to trait (assoc const)`() = doTest("""
    //- lib.rs
        mod mod1 {
            struct Foo/*caret*/;
            pub trait Trait/*caret*/ { const C: i32 = 0; }
            impl Trait for Foo/*caret*/ {}
            fn func/*caret*/() { let _ = Foo::C; }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            struct Foo;

            pub trait Trait { const C: i32 = 0; }

            impl Trait for Foo {}

            fn func() { let _ = Foo::C; }
        }
    """)

    fun `test inside reference to moved trait (assoc const)`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub trait Trait/*caret*/ { const C: i32 = 0; }
            pub struct Foo;
            impl Trait for Foo/*caret*/ {}
            fn func() { let _ = Foo::C; }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use crate::mod2::Trait;

            pub struct Foo;

            fn func() { let _ = Foo::C; }
        }
        mod mod2 {
            use crate::mod1::Foo;

            pub trait Trait { const C: i32 = 0; }

            impl Trait for Foo {}
        }
    """)


    fun `test outside references which starts with super 1`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub fn inner1_func() {}
            pub mod inner2 {
                pub fn inner2_func() {}
                mod mod1 {
                    fn foo/*caret*/() {
                        super::super::inner1_func();
                        super::inner2_func();
                    }
                }
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod inner1 {
            pub fn inner1_func() {}
            pub mod inner2 {
                pub fn inner2_func() {}
                mod mod1 {}
            }
        }
        mod mod2 {
            use crate::inner1;
            use crate::inner1::inner2;

            fn foo() {
                inner1::inner1_func();
                inner2::inner2_func();
            }
        }
    """)

    fun `test outside references which starts with super 2`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub fn inner1_func() {}
            pub mod inner2 {
                pub fn inner2_func() {}
                mod mod1 {
                    fn foo1/*caret*/() {
                        use super::super::inner1_func;
                        inner1_func();

                        use super::inner2_func;
                        inner2_func();
                    }

                    fn foo2/*caret*/() {
                        use super::super::*;
                        inner1_func();

                        use super::*;
                        inner2_func();
                    }
                }
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod inner1 {
            pub fn inner1_func() {}
            pub mod inner2 {
                pub fn inner2_func() {}
                mod mod1 {}
            }
        }
        mod mod2 {
            fn foo1() {
                use crate::inner1::inner1_func;
                inner1_func();

                use crate::inner1::inner2::inner2_func;
                inner2_func();
            }

            fn foo2() {
                use crate::inner1::*;
                inner1_func();

                use crate::inner1::inner2::*;
                inner2_func();
            }
        }
    """)

    fun `test outside references to items in new mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo1/*caret*/() { crate::mod2::bar1(); }
            fn foo2/*caret*/() {
                use crate::mod2;
                mod2::bar1();
            }
            mod inner/*caret*/ {
                fn foo3() { crate::mod2::bar1(); }
                fn foo4() {
                    use crate::mod2;
                    mod2::bar1();
                }
            }
        }
        mod mod2/*target*/ {
            pub fn bar1() {}
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub fn bar1() {}

            fn foo1() { bar1(); }

            fn foo2() {
                use crate::mod2;
                bar1();
            }

            mod inner {
                fn foo3() { crate::mod2::bar1(); }
                fn foo4() {
                    use crate::mod2;
                    mod2::bar1();
                }
            }
        }
    """)

    fun `test outside references to items in submodule of new mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo1/*caret*/() {
                crate::mod2::inner1::bar1();
                crate::mod2::inner1::bar2();
            }
            fn foo2/*caret*/() {
                use crate::mod2::inner1;
                inner1::bar1();
                inner1::bar2();
            }
            mod inner/*caret*/ {
                fn foo3() {
                    crate::mod2::inner1::bar1();
                    crate::mod2::inner1::bar2();
                }
                fn foo4() {
                    use crate::mod2::inner1;
                    inner1::bar1();
                    inner1::bar2();
                }
            }
        }
        mod mod2/*target*/ {
            pub mod inner1 {
                pub use inner2::*;
                mod inner2 { pub fn bar2() {} }
                pub fn bar1() {}
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub mod inner1 {
                pub use inner2::*;
                mod inner2 { pub fn bar2() {} }
                pub fn bar1() {}
            }

            fn foo1() {
                crate::mod2::inner1::bar1();
                crate::mod2::inner1::bar2();
            }

            fn foo2() {
                inner1::bar1();
                inner1::bar2();
            }

            mod inner {
                fn foo3() {
                    crate::mod2::inner1::bar1();
                    crate::mod2::inner1::bar2();
                }
                fn foo4() {
                    use crate::mod2::inner1;
                    inner1::bar1();
                    inner1::bar2();
                }
            }
        }
    """)

    fun `test outside reference to static method`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {
                Bar::func();
            }
            pub struct Bar {}
            impl Bar { pub fn func() {} }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub struct Bar {}
            impl Bar { pub fn func() {} }
        }
        mod mod2 {
            use crate::mod1::Bar;

            fn foo() {
                Bar::func();
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test outside reference to items from prelude`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {
                let _ = String::new();
                let _ = Some(1);
                let _ = Vec::<i32>::new();
                let _: Vec<i32> = Vec::new();
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            fn foo() {
                let _ = String::new();
                let _ = Some(1);
                let _ = Vec::<i32>::new();
                let _: Vec<i32> = Vec::new();
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test outside references to macros from prelude`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {
                println!("foo");
                let _ = format!("{}", 1);
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            fn foo() {
                println!("foo");
                let _ = format!("{}", 1);
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test outside reference to items from stdlib`() = doTest("""
    //- lib.rs
        mod mod1 {
            use std::fs;
            fn foo/*caret*/() {
                let _ = fs::read_dir(".");
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            use std::fs;

            fn foo() {
                let _ = fs::read_dir(".");
            }
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test outside reference to Arc from stdlib`() = doTest("""
    //- lib.rs
        mod mod1 {
            use std::sync::Arc;
            fn foo/*caret*/(_: Arc<i32>) {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            use std::sync::Arc;

            fn foo(_: Arc<i32>) {}
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test outside reference to Debug trait in derive`() = doTest("""
    //- lib.rs
        mod mod1 {
            #[derive(Debug)]
            struct Foo/*caret*/ {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            #[derive(Debug)]
            struct Foo {}
        }
    """)

    fun `test outside reference inside println macro`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {
                println!("{}", BAR);
            }
            pub const BAR: i32 = 0;
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub const BAR: i32 = 0;
        }
        mod mod2 {
            use crate::mod1::BAR;

            fn foo() {
                println!("{}", BAR);
            }
        }
    """)

    fun `test outside references, should add import for parent mod when target mod has same name in scope`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {
                let _ = Bar {};
            }
            pub struct Bar {}
        }
        mod mod2/*target*/ {
            struct Bar {}
        }
    """, """
    //- lib.rs
        mod mod1 {
            pub struct Bar {}
        }
        mod mod2 {
            use crate::mod1;

            struct Bar {}

            fn foo() {
                let _ = mod1::Bar {};
            }
        }
    """)

    fun `test self references 1`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo1/*caret*/() { bar1(); }
            fn foo2/*caret*/() { crate::mod1::bar2(); }
            fn bar1/*caret*/() {}
            fn bar2/*caret*/() {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            fn foo1() { bar1(); }

            fn foo2() { bar2(); }

            fn bar1() {}

            fn bar2() {}
        }
    """)

    fun `test self references from inner mod 1`() = doTest("""
    //- lib.rs
        mod mod1 {
            mod foo1/*caret*/ {
                use crate::mod1;
                fn test() { mod1::bar1(); }
            }
            mod foo2/*caret*/ {
                use crate::mod1::*;
                fn test() { bar2(); }
            }
            fn bar1/*caret*/() {}
            fn bar2/*caret*/() {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            mod foo1 {
                use crate::mod2;
                fn test() { mod2::bar1(); }
            }

            mod foo2 {
                use crate::mod2::bar2;

                fn test() { bar2(); }
            }

            fn bar1() {}

            fn bar2() {}
        }
    """)

    fun `test self references from inner mod 2`() = doTest("""
    //- lib.rs
        mod mod1 {
            mod foo1/*caret*/ {
                use crate::mod1::Bar1;
                fn test() { let _ = Bar1 {}; }
            }
            mod foo2/*caret*/ {
                use crate::mod1::*;
                fn test() { let _ = Bar2 {}; }
            }
            struct Bar1/*caret*/ {}
            struct Bar2/*caret*/ {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            mod foo1 {
                use crate::mod2::Bar1;
                fn test() { let _ = Bar1 {}; }
            }

            mod foo2 {
                use crate::mod2::Bar2;

                fn test() { let _ = Bar2 {}; }
            }

            struct Bar1 {}

            struct Bar2 {}
        }
    """)

    fun `test self references from inner mod using super`() = doTest("""
    //- lib.rs
        mod mod1 {
            mod foo/*caret*/ {
                mod test {
                    use super::*;

                    fn baz() {
                        bar();
                    }
                }
                fn bar() {}
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            mod foo {
                mod test {
                    use super::*;

                    fn baz() {
                        bar();
                    }
                }
                fn bar() {}
            }
        }
    """)

    fun `test self references to inner mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            mod foo1/*caret*/ {
                pub fn func() {}
            }
            fn foo2/*caret*/() { foo1::func(); }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            mod foo1 {
                pub fn func() {}
            }

            fn foo2() { foo1::func(); }
        }
    """)

    fun `test inside references from new mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub mod inner1/*caret*/ {
                pub use inner2::*;
                mod inner2 { pub fn foo3() {} }
                pub fn foo2() {}
            }
            pub fn foo1/*caret*/() {}
            pub fn foo4/*caret*/() {}
        }
        mod mod2/*target*/ {
            fn bar1() {
                crate::mod1::foo1();
                crate::mod1::inner1::foo2();
                crate::mod1::inner1::foo3();
            }
            fn bar2() {
                use crate::mod1::foo4;
                foo4();
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            fn bar1() {
                foo1();
                inner1::foo2();
                inner1::foo3();
            }
            fn bar2() {
                foo4();
            }

            pub mod inner1 {
                pub use inner2::*;
                mod inner2 { pub fn foo3() {} }
                pub fn foo2() {}
            }

            pub fn foo1() {}

            pub fn foo4() {}
        }
    """)

    fun `test inside references from child mod of new mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {
            mod inner1 {
                fn test() { crate::mod1::foo(); }
            }
            mod inner2 {
                fn test() {
                    use crate::mod1;
                    mod1::foo();
                }
            }
            mod inner3 {
                fn test() {
                    use crate::mod1::*;
                    foo();
                }
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            mod inner1 {
                fn test() { crate::mod2::foo(); }
            }
            mod inner2 {
                fn test() {
                    use crate::{mod1, mod2};
                    mod2::foo();
                }
            }
            mod inner3 {
                fn test() {
                    use crate::mod1::*;
                    use crate::mod2::foo;
                    foo();
                }
            }

            pub fn foo() {}
        }
    """)

    // it is idiomatic to import parent mod for functions, and directly import structs/enums
    // https://doc.rust-lang.org/book/ch07-04-bringing-paths-into-scope-with-the-use-keyword.html#creating-idiomatic-use-paths
    fun `test inside references from old mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo1/*caret*/() {}
            pub struct Foo2/*caret*/ {}
            pub enum Foo3/*caret*/ { V1 }
            fn bar() {
                foo1();
                let _ = Foo2 {};
                let _ = Foo3::V1;
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use crate::mod2;
            use crate::mod2::{Foo2, Foo3};

            fn bar() {
                mod2::foo1();
                let _ = Foo2 {};
                let _ = Foo3::V1;
            }
        }
        mod mod2 {
            pub fn foo1() {}

            pub struct Foo2 {}

            pub enum Foo3 { V1 }
        }
    """)

    fun `test inside references absolute`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo1/*caret*/() {}
            pub struct Foo2/*caret*/ {}
        }
        mod mod2/*target*/ {}

        mod usage {
            fn test() {
                crate::mod1::foo1();
                let _ = crate::mod1::Foo2 {};
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub fn foo1() {}

            pub struct Foo2 {}
        }

        mod usage {
            fn test() {
                crate::mod2::foo1();
                let _ = crate::mod2::Foo2 {};
            }
        }
    """)

    fun `test inside references starting with super`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo1/*caret*/() {}
            pub struct Foo2/*caret*/ {}

            mod usage {
                fn test() {
                    super::foo1();
                    let _ = super::Foo2 {};
                }
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            mod usage {
                use crate::mod2;
                use crate::mod2::Foo2;

                fn test() {
                    mod2::foo1();
                    let _ = Foo2 {};
                }
            }
        }
        mod mod2 {
            pub fn foo1() {}

            pub struct Foo2 {}
        }
    """)

    fun `test inside references, should add fully qualified import`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo1/*caret*/() {}
            pub struct Foo2/*caret*/ {}
        }
        mod mod2/*target*/ {}

        mod usage {
            use crate::mod1::foo1;
            use crate::mod1::Foo2;
            fn test() {
                foo1();
                let _ = Foo2 {};
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub fn foo1() {}

            pub struct Foo2 {}
        }

        mod usage {
            use crate::mod2::foo1;
            use crate::mod2::Foo2;
            fn test() {
                foo1();
                let _ = Foo2 {};
            }
        }
    """)

    fun `test inside references, should add import for parent mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo1/*caret*/() {}
            pub struct Foo2/*caret*/ {}
        }
        mod mod2/*target*/ {}

        mod usage {
            use crate::mod1;
            fn test() {
                mod1::foo1();
                let _ = mod1::Foo2 {};
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub fn foo1() {}

            pub struct Foo2 {}
        }

        mod usage {
            use crate::mod2;
            fn test() {
                mod2::foo1();
                let _ = mod2::Foo2 {};
            }
        }
    """)

    fun `test inside references, should add import for grandparent mod`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub mod mod1 {
                pub fn foo1/*caret*/() {}
                pub struct Foo2/*caret*/ {}
            }
        }
        mod inner2 {
            pub mod mod2/*target*/ {}
        }

        mod usage {
            use crate::inner1;

            fn test() {
                inner1::mod1::foo1();
                let _ = inner1::mod1::Foo2 {};
            }
        }
    """, """
    //- lib.rs
        mod inner1 {
            pub mod mod1 {}
        }
        mod inner2 {
            pub mod mod2 {
                pub fn foo1() {}

                pub struct Foo2 {}
            }
        }

        mod usage {
            use crate::inner2;

            fn test() {
                inner2::mod2::foo1();
                let _ = inner2::mod2::Foo2 {};
            }
        }
    """)

    fun `test inside reference when parent of public target mod reexports item from target mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {}
            fn bar() {
                foo();
            }
        }
        mod inner {
            // can't use this reexport for foo
            pub use mod2::bar;
            pub mod mod2/*target*/ {
                pub fn bar() {}
            }
        }
    """, """
    //- lib.rs
        mod mod1 {
            use crate::inner::mod2;

            fn bar() {
                mod2::foo();
            }
        }
        mod inner {
            // can't use this reexport for foo
            pub use mod2::bar;
            pub mod mod2 {
                pub fn bar() {}

                pub fn foo() {}
            }
        }
    """)

    fun `test inside reference when parent of private target mod reexports item from target mod`() = doTestConflictsError("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {}
            fn bar() {
                foo();
            }
        }
        mod inner {
            pub use mod2::bar;  // can't use this reexport for foo
            mod mod2/*target*/ {
                pub fn bar() {}
            }
        }
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

    fun `test outside references to generic struct`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {
                let _ = Bar::<i32> { field: 0 };
            }
            pub struct Bar<T> { pub field: T }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub struct Bar<T> { pub field: T }
        }
        mod mod2 {
            use crate::mod1::Bar;

            fn foo() {
                let _ = Bar::<i32> { field: 0 };
            }
        }
    """)

    fun `test outside references to generic function`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {
                bar::<i32>();
            }
            pub fn bar<T>() {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub fn bar<T>() {}
        }
        mod mod2 {
            use crate::mod1;

            fn foo() {
                mod1::bar::<i32>();
            }
        }
    """)

    fun `test self references to generic function`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo1<T>/*caret*/() {}
            fn foo2/*caret*/() {
                foo1::<i32>();
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            fn foo1<T>() {}

            fn foo2() {
                foo1::<i32>();
            }
        }
    """)

    fun `test inside references to generic function`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo<T>/*caret*/() {}
        }
        mod mod2/*target*/ {}
        fn main() {
            mod1::foo::<i32>();
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub fn foo<T>() {}
        }
        fn main() {
            mod2::foo::<i32>();
        }
    """)

    fun `test inside references to generic function parametrized by generic struct`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo<T>/*caret*/() {}
            pub struct Foo<T>/*caret*/ {}
        }
        mod mod2/*target*/ {}
        fn main() {
            mod1::foo::<mod1::Foo<i32>>();
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub fn foo<T>() {}

            pub struct Foo<T> {}
        }
        fn main() {
            mod2::foo::<mod2::Foo<i32>>();
        }
    """)

    fun `test inside references from new mod in use group`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo1/*caret*/() {}
            pub fn foo2/*caret*/() {}
        }
        mod mod2/*target*/ {
            use crate::mod1::{foo1, foo2};
            fn bar() {
                foo1();
                foo2();
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            fn bar() {
                foo1();
                foo2();
            }

            pub fn foo1() {}

            pub fn foo2() {}
        }
    """)

    fun `test inside references from use group 1`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo1/*caret*/() {}
            pub fn foo2/*caret*/() {}
            pub fn bar() {}
        }
        mod mod2/*target*/ {}
        mod usage {
            use crate::mod1::{foo1, foo2, bar};

            fn foo() {
                foo1();
                foo2();
                bar();
            }
        }
    """, """
    //- lib.rs
        mod mod1 {
            pub fn bar() {}
        }
        mod mod2 {
            pub fn foo1() {}

            pub fn foo2() {}
        }
        mod usage {
            use crate::mod1::bar;
            use crate::mod2::{foo1, foo2};

            fn foo() {
                foo1();
                foo2();
                bar();
            }
        }
    """)

    fun `test inside references from use group 2`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub mod foo1/*caret*/ {
                pub fn foo1_func() {}
                pub mod foo1_inner {
                    pub fn foo1_inner_func() {}
                }
            }
            pub mod foo2/*caret*/ {
                pub fn foo2_func() {}
            }
        }
        mod mod2/*target*/ {}
        mod usage1 {
            use crate::mod1::{
                foo1::{foo1_func, foo1_inner::foo1_inner_func},
                foo2::foo2_func,
            };

            fn foo() {
                foo1_func();
                foo1_inner_func();
                foo2_func();
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub mod foo1 {
                pub fn foo1_func() {}
                pub mod foo1_inner {
                    pub fn foo1_inner_func() {}
                }
            }

            pub mod foo2 {
                pub fn foo2_func() {}
            }
        }
        mod usage1 {
            use crate::mod2::foo1::{foo1_func, foo1_inner::foo1_inner_func};
            use crate::mod2::foo2::foo2_func;

            fn foo() {
                foo1_func();
                foo1_inner_func();
                foo2_func();
            }
        }
    """)

    // todo grouping use items with same attributes and visibility
    fun `test inside references from use group with attributes and visibility`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo1/*caret*/() {}
            pub fn foo2/*caret*/() {}
            pub fn bar() {}
        }
        mod mod2/*target*/ {}
        mod usage {
            #[attr]
            pub use crate::mod1::{foo1, foo2, bar};
        }
    """, """
    //- lib.rs
        mod mod1 {
            pub fn bar() {}
        }
        mod mod2 {
            pub fn foo1() {}

            pub fn foo2() {}
        }
        mod usage {
            #[attr]
            pub use crate::mod1::bar;
            #[attr]
            pub use crate::mod2::foo1;
            #[attr]
            pub use crate::mod2::foo2;
        }
    """)

    fun `test outside reference to enum variant in old mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            use Bar::*;
            pub enum Bar { Bar1, Bar2 }
            fn foo/*caret*/() {
                let _ = Bar1;
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use Bar::*;
            pub enum Bar { Bar1, Bar2 }
        }
        mod mod2 {
            use crate::mod1::Bar::Bar1;

            fn foo() {
                let _ = Bar1;
            }
        }
    """)

    fun `test outside reference to enum variant in other mod`() = doTest("""
    //- lib.rs
        mod mod1 {
            use crate::bar::Bar::*;
            fn foo/*caret*/() {
                let _ = Bar1;
            }
        }
        mod mod2/*target*/ {}
        mod bar {
            pub enum Bar { Bar1, Bar2 }
        }
    """, """
    //- lib.rs
        mod mod1 {
            use crate::bar::Bar::*;
        }
        mod mod2 {
            use crate::bar::Bar::Bar1;

            fn foo() {
                let _ = Bar1;
            }
        }
        mod bar {
            pub enum Bar { Bar1, Bar2 }
        }
    """)

    fun `test outside reference to enum variant in match`() = doTest("""
    //- lib.rs
        mod mod1 {
            use Bar::*;
            pub enum Bar { Bar1, Bar2 }
            fn foo/*caret*/(bar: Bar) {
                match bar {
                    Bar1 | Bar2 => ()
                }
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use Bar::*;
            pub enum Bar { Bar1, Bar2 }
        }
        mod mod2 {
            use crate::mod1::Bar;
            use crate::mod1::Bar::{Bar1, Bar2};

            fn foo(bar: Bar) {
                match bar {
                    Bar1 | Bar2 => ()
                }
            }
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

    fun `test move to other crate 1`() = doTest("""
    //- main.rs
        mod mod1 {
            pub fn foo/*caret*/() {}
            fn bar() {
                foo();
            }
        }
    //- lib.rs
        pub mod mod2/*target*/ {}
    """, """
    //- main.rs
        mod mod1 {
            use test_package::mod2;

            fn bar() {
                mod2::foo();
            }
        }
    //- lib.rs
        pub mod mod2 {
            pub fn foo() {}
        }
    """)

    fun `test move to other crate 2`() = doTest("""
    //- lib.rs
        pub mod mod1 {
            fn foo/*caret*/() {
                bar();
            }
            pub fn bar() {}
        }
    //- main.rs
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        pub mod mod1 {
            pub fn bar() {}
        }
    //- main.rs
        mod mod2 {
            use test_package::mod1;

            fn foo() {
                mod1::bar();
            }
        }
    """)

    fun `test absolute reference to library crate when move from library to binary`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo/*caret*/() {
                crate::bar();
            }
        }
        pub fn bar() {}
    //- main.rs
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        pub fn bar() {}
    //- main.rs
        mod mod2 {
            fn foo() {
                test_package::bar();
            }
        }
    """)

    fun `test absolute reference to library crate when move from binary to library`() = doTest("""
    //- main.rs
        mod mod1 {
            fn foo/*caret*/() {
                test_package::bar();
            }
        }
    //- lib.rs
        mod mod2/*target*/ {}
        pub fn bar() {}
    """, """
    //- main.rs
        mod mod1 {}
    //- lib.rs
        mod mod2 {
            fn foo() {
                crate::bar();
            }
        }
        pub fn bar() {}
    """)

    fun `test absolute reference to binary crate when move from binary to library`() = doTestConflictsError("""
    //- main.rs
        mod mod1 {
            fn foo/*caret*/() {
                crate::bar();
            }
        }
        pub fn bar() {}
    //- lib.rs
        mod mod2/*target*/ {}
    """)

    fun `test outside references with import alias`() = doTest("""
    //- lib.rs
        mod foo {
            pub struct Foo {}
        }
        mod mod1 {
            use crate::foo::Foo as Bar;
            pub fn func/*caret*/(foo: Bar) {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod foo {
            pub struct Foo {}
        }
        mod mod1 {}
        mod mod2 {
            use crate::foo::Foo as Bar;

            pub fn func(foo: Bar) {}
        }
    """)

    fun `test self references with import alias`() = doTest("""
    //- lib.rs
        mod mod1 {
            mod inner1/*caret*/ {
                pub struct Foo;
            }
            mod inner2/*caret*/ {
                use crate::mod1::inner1::Foo as Bar;
                fn test(bar: Bar) {}
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            mod inner1 {
                pub struct Foo;
            }

            mod inner2 {
                use crate::mod2::inner1::Foo as Bar;
                fn test(bar: Bar) {}
            }
        }
    """)

    fun `test inside references with import alias`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {}
        mod usage {
            use crate::mod1::foo as bar;
            fn test() {
                bar();
            }
        }
    """, """
    //- lib.rs
        mod mod1 {}
        mod mod2 {
            pub fn foo() {}
        }
        mod usage {
            use crate::mod2::foo as bar;
            fn test() {
                bar();
            }
        }
    """)

    fun `test don't reorder use items`() = doTest("""
    //- lib.rs
        mod mod1 {
            mod inner {}
            use aaa;
            fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            mod inner {}
            use aaa;
        }
        mod mod2 {
            fn foo() {}
        }
    """)

    fun `test insert import at correct location`() = doTest("""
    //- lib.rs
        mod mod1 {
            use crate::mod1::A;
            use crate::mod3::C;
            fn foo/*caret*/() {}
            fn bar() {
                foo();
            }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            use crate::mod1::A;
            use crate::mod2;
            use crate::mod3::C;

            fn bar() {
                mod2::foo();
            }
        }
        mod mod2 {
            pub fn foo() {}
        }
    """)

    fun `test move create file 1`() = doTestCreateFile("foo.rs", """
    //- lib.rs
        fn func/*caret*/() {}
    """, """
    //- lib.rs
        mod foo;
    //- foo.rs
        fn func() {}
    """)

    fun `test move create file 2`() = doTestCreateFile("foo/mod.rs", """
    //- lib.rs
        fn func/*caret*/() {}
    """, """
    //- lib.rs
        mod foo;
    //- foo/mod.rs
        fn func() {}
    """)

    fun `test move create file 3`() = doTestCreateFile("inner/foo.rs", """
    //- lib.rs
        mod inner;
        fn func/*caret*/() {}
    //- inner.rs
    """, """
    //- lib.rs
        mod inner;
    //- inner.rs
        mod foo;
    //- inner/foo.rs
        fn func() {}
    """)

    fun `test move create file 4`() = doTestCreateFile("inner/foo/mod.rs", """
    //- lib.rs
        mod inner;
        fn func/*caret*/() {}
    //- inner.rs
    """, """
    //- lib.rs
        mod inner;
    //- inner.rs
        mod foo;
    //- inner/foo/mod.rs
        fn func() {}
    """)
}
