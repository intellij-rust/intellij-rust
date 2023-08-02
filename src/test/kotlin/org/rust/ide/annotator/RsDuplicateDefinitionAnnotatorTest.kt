/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.*

class RsDuplicateDefinitionAnnotatorTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test name duplication in struct E0124`() = checkErrors("""
        struct S {
            no_dup: bool,
            <error descr="Field `dup` is already declared [E0124]">dup</error>: f64,
            <error descr="Field `dup` is already declared [E0124]">dup</error>: f64
        }

        enum E {
            VAR1 {
                no_dup: bool
            },
            VAR2 {
                no_dup: bool,
                <error descr="Field `dup` is already declared [E0124]">dup</error>: f64,
                <error descr="Field `dup` is already declared [E0124]">dup</error>: f64
            }
        }
    """)

    fun `test name duplication in impl E0201`() = checkErrors("""
        struct Foo;
        impl Foo {
            fn fn_unique() {}
            fn <error descr="Duplicate definitions with name `dup` [E0201]">dup</error>(&self, a: u32) {}
            fn <error descr="Duplicate definitions with name `dup` [E0201]">dup</error>(&self, a: u32) {}
        }

        trait Bar {
            const UNIQUE: u32;
            const TRAIT_DUP: u32;
            fn unique() {}
            fn trait_dup() {}
        }
        impl Bar for Foo {
            const UNIQUE: u32 = 14;
            const <error descr="Duplicate definitions with name `TRAIT_DUP` [E0201]">TRAIT_DUP</error>: u32 = 101;
            const <error descr="Duplicate definitions with name `TRAIT_DUP` [E0201]">TRAIT_DUP</error>: u32 = 101;
            fn unique() {}
            fn <error descr="Duplicate definitions with name `trait_dup` [E0201]">trait_dup</error>() {}
            fn <error descr="Duplicate definitions with name `trait_dup` [E0201]">trait_dup</error>() {}
        }
    """)

    fun `test lifetime name duplication in generic params E0403`() = checkErrors("""
        fn foo<'a, 'b>(x: &'a str, y: &'b str) { }
        struct Str<'a, 'b> { a: &'a u32, b: &'b f64 }
        impl<'a, 'b> Str<'a, 'b> {}
        enum Direction<'a, 'b> { LEFT(&'a str), RIGHT(&'b str) }
        trait Trait<'a, 'b> {}

        fn bar<<error descr="The name `'a` is already used for a generic parameter in this item's generic parameters [E0403]">'a</error>, 'b, <error>'a</error>>(x: &'a str, y: &'b str) { }
        struct St<<error>'a</error>, 'b, <error>'a</error>> { a: &'a u32, b: &'b f64 }
        impl<<error>'a</error>, 'b, <error>'a</error>> Str<'a, 'b> {}
        enum Dir<<error>'a</error>, 'b, <error>'a</error>> { LEFT(&'a str), RIGHT(&'b str) }
        trait Tr<<error>'a</error>, 'b, <error>'a</error>> {}
    """)

    @MockRustcVersion("1.56.0")
    fun `test name duplication in generic params E0403`() = checkErrors("""
        fn f1<T1, T2>() {}
        fn f2<T1, const T2: i32>() {}
        fn f3<const T1: i32, const T2: i32>() {}
        fn f4<
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            <error descr="The name `T2` is already used for a generic parameter in this item's generic parameters [E0403]">T2</error>,
            T3,
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            const <error descr="The name `T4` is already used for a generic parameter in this item's generic parameters [E0403]">T4</error>: i32,
            const T5: i32,
            const <error descr="The name `T4` is already used for a generic parameter in this item's generic parameters [E0403]">T4</error>: i32,
            const <error descr="The name `T2` is already used for a generic parameter in this item's generic parameters [E0403]">T2</error>: i32
        >() {}

        struct S1<T1, T2> { t1: T1, t2: T2 }
        struct S2<T1, const T2: i32> { t1: T1 }
        struct S3<const T1: i32, const T2: i32> {}
        struct S4<
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            <error descr="The name `T2` is already used for a generic parameter in this item's generic parameters [E0403]">T2</error>,
            T3,
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            const <error descr="The name `T4` is already used for a generic parameter in this item's generic parameters [E0403]">T4</error>: i32,
            const T5: i32,
            const <error descr="The name `T4` is already used for a generic parameter in this item's generic parameters [E0403]">T4</error>: i32,
            const <error descr="The name `T2` is already used for a generic parameter in this item's generic parameters [E0403]">T2</error>: i32
        > { t: T1, p: T2 }

        impl<T1, T2> S1<T1, T2> {}
        impl<
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            T2,
            T3,
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>
        > S4<T1, T2, T3, T1, 0, 0, 0, 0> {}

        enum E1<T1, T2> { L(T1), R(T2) }
        enum E2<T1, const T2: i32> { L(T1) }
        enum E3<const T1: i32, const T2: i32> {}
        enum E4<
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            <error descr="The name `T2` is already used for a generic parameter in this item's generic parameters [E0403]">T2</error>,
            T3,
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            const <error descr="The name `T4` is already used for a generic parameter in this item's generic parameters [E0403]">T4</error>: i32,
            const T5: i32,
            const <error descr="The name `T4` is already used for a generic parameter in this item's generic parameters [E0403]">T4</error>: i32,
            const <error descr="The name `T2` is already used for a generic parameter in this item's generic parameters [E0403]">T2</error>: i32
        > { L(T1), M(T2), R(T3) }

        trait Tr1<T1, T2> {}
        trait Tr2<T1, const T2: i32> {}
        trait Tr3<const T1: i32, const T2: i32> {}
        trait Tr4<
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            <error descr="The name `T2` is already used for a generic parameter in this item's generic parameters [E0403]">T2</error>,
            T3,
            <error descr="The name `T1` is already used for a generic parameter in this item's generic parameters [E0403]">T1</error>,
            const <error descr="The name `T4` is already used for a generic parameter in this item's generic parameters [E0403]">T4</error>: i32,
            const T5: i32,
            const <error descr="The name `T4` is already used for a generic parameter in this item's generic parameters [E0403]">T4</error>: i32,
            const <error descr="The name `T2` is already used for a generic parameter in this item's generic parameters [E0403]">T2</error>: i32
        > {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test name duplication in param list E0415`() = checkErrors("""
        fn foo(x: u32, X: u32) {}
        fn bar<T>(T: T) {}

        fn simple(<error descr="Identifier `a` is bound more than once in this parameter list [E0415]">a</error>: u32,
                  b: bool,
                  <error>a</error>: f64) {}
        fn tuples(<error>a</error>: u8, (b, (<error>a</error>, c)): (u16, (u32, u64))) {}
        fn fn_ptrs(x: i32, y: fn (x: i32, y: i32), z: fn (x: i32, x: i32)) {}
        fn cfg_1(#[cfg(intellij_rust)] a: i32, #[cfg(not(intellij_rust))] a: u32) {}
        fn cfg_2(#[cfg(intellij_rust)] <error>a</error>: i32, #[cfg(intellij_rust)] <error>a</error>: u32) {}
        fn cfg_3(#[cfg(intellij_rust)] <error>a</error>: i32, <error>a</error>: u32) {}

        trait Foo {
            fn foo(&self,
                   <error descr="Identifier `x` is bound more than once in this parameter list [E0415]">x</error>: i32,
                   <error descr="Identifier `x` is bound more than once in this parameter list [E0415]">x</error>: i32
            ) {}
        }
    """)

    fun `test name duplication in param list of non-default trait methods`() = checkErrors("""
        trait Foo {
            fn bar(x: i32, x: i32);
        }
    """)

    fun `test name duplication in code block E0428`() = checkErrors("""
        fn abc() {
            const UNIQUE_CONST: i32 = 10;
            static UNIQUE_STATIC: f64 = 0.72;
            fn unique_fn() {}
            struct UniqueStruct;
            trait UniqueTrait {}
            enum UniqueEnum {}
            mod unique_mod {}

            const  <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>: u32 = 20;
            static <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>: i64 = -1.3;
            fn     <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>() {}
            struct <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
            trait  <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
            enum   <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
            mod    <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
        }
    """)

    fun `test no E0428 multiple underscore constants`() = checkErrors("""
        const _: i32 = 1;
        const _: i32 = 1;
    """)

    fun `test name duplication in enum E0428`() = checkErrors("""
        enum Directions {
            NORTH,
            <error descr="The name `SOUTH` is defined multiple times [E0428]">SOUTH</error> { distance: f64 },
            WEST,
            <error descr="The name `SOUTH` is defined multiple times [E0428]">SOUTH</error> { distance: f64 },
            EAST
        }
    """)

    fun `test name duplication in foreign mod E0428`() = checkErrors("""
        extern "C" {
            static mut UNIQUE: u16;
            fn unique();

            static mut <error descr="The name `DUP` is defined multiple times [E0428]">DUP</error>: u32;
            static mut <error descr="The name `DUP` is defined multiple times [E0428]">DUP</error>: u32;

            fn <error descr="The name `dup` is defined multiple times [E0428]">dup</error>();
            fn <error descr="The name `dup` is defined multiple times [E0428]">dup</error>();
        }
    """)

    fun `test name duplication in file E0428`() = checkErrors("""
        const UNIQUE_CONST: i32 = 10;
        static UNIQUE_STATIC: f64 = 0.72;
        fn unique_fn() {}
        struct UniqueStruct;
        trait UniqueTrait {}
        enum UniqueEnum {}
        mod unique_mod {}

        const  <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>: u32 = 20;
        static <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>: i64 = -1.3;
        fn     <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>() {}
        struct <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
        trait  <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
        enum   <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
        mod    <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
    """)

    fun `test name duplication in module E0428`() = checkErrors("""
        mod foo {
            const UNIQUE_CONST: i32 = 10;
            static UNIQUE_STATIC: f64 = 0.72;
            fn unique_fn() {}
            struct UniqueStruct;
            trait UniqueTrait {}
            enum UniqueEnum {}
            mod unique_mod {}

            const  <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>: u32 = 20;
            static <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>: i64 = -1.3;
            fn     <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error>() {}
            struct <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
            trait  <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
            enum   <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
            mod    <error descr="The name `Dup` is defined multiple times [E0428]">Dup</error> {}
        }
    """)

    fun `test name duplication in trait E0428`() = checkErrors("""
        trait T {
            type NO_DUP_T;
            const NO_DUP_C: u8;
            fn no_dup_f();

            type <error descr="The name `DUP_T` is defined multiple times [E0428]">DUP_T</error>;
            type <error descr="The name `DUP_T` is defined multiple times [E0428]">DUP_T</error>;

            const <error descr="The name `DUP_C` is defined multiple times [E0428]">DUP_C</error>: u32;
            const <error descr="The name `DUP_C` is defined multiple times [E0428]">DUP_C</error>: u32;

            fn <error descr="The name `dup` is defined multiple times [E0428]">dup</error>(&self);
            fn <error descr="The name `dup` is defined multiple times [E0428]">dup</error>(&self);
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test respects namespaces E0428`() = checkErrors("""
        #![feature(decl_macro)]

        mod m {
            // Consts and types are in different namespaces
            type  NO_C_DUP = bool;
            const NO_C_DUP: u32 = 10;

            // Functions and types are in different namespaces
            type NO_F_DUP = u8;
            fn   NO_F_DUP() {}

            // Consts and functions are in the same namespace (values)
            fn <error descr="The name `DUP_V` is defined multiple times [E0428]">DUP_V</error>() {}
            const <error>DUP_V</error>: u8 = 1;

            // Enums and traits are in the same namespace (types)
            trait <error descr="The name `DUP_T` is defined multiple times [E0428]">DUP_T</error> {}
            enum <error>DUP_T</error> {}

            mod <error>foo</error>;
            fn foo() {}

            // Macros have thair own namespace
            type NO_M_DUP = u16;
            macro NO_M_DUP(){}
        }
    """)


    fun `test ignores local bindings E0428`() = checkErrors("""
        mod no_dup {
            fn no_dup() {
                let no_dup: bool = false;
                fn no_dup(no_dup: u23) {
                    mod no_dup {}
                }
            }
        }
    """)

    fun `test ignores inner containers E0428`() = checkErrors("""
        mod foo {
            const NO_DUP: u8 = 4;
            fn f() {
                const NO_DUP: u8 = 7;
                { const NO_DUP: u8 = 9; }
            }
            struct S { NO_DUP: u8 }
            trait T { const NO_DUP: u8 = 3; }
            enum E { NO_DUP }
            mod m { const NO_DUP: u8 = 1; }
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test respects cfg attribute E0428`() = checkErrors("""
        mod opt {
            #[cfg(not(bar))] mod foo {}
            #[cfg(bar)]      mod foo {}

            #[cfg(intellij_rust)] fn <error descr="The name `hello_world` is defined multiple times [E0428]">hello_world</error>() {}
            fn <error descr="The name `hello_world` is defined multiple times [E0428]">hello_world</error>() {}

            #[cfg(bar)] fn hello_rust() {}
            fn hello_rust() {}
        }
    """)

    fun `test macro mod fn no E0428`() = checkErrors("""
        macro_rules! example {
            () => ()
        }
        mod example { }
        fn example() { }
    """)

    fun `test macro struct no E0428`() = checkErrors("""
        macro_rules! example {
            () => ()
        }
        struct example { }
    """)

    fun `test duplicate macro no E0428`() = checkErrors("""
        macro_rules! example {
            () => ()
        }
        macro_rules! example {
            () => ()
        }
    """)

    fun `test duplicates with import E0252`() = checkErrors("""
        use bar::{<error descr="The name `test1` is defined multiple times [E0255]">test1</error>};
        fn <error descr="The name `test1` is defined multiple times [E0255]">test1</error>(){}

        use baz::<error descr="The name `test2` is defined multiple times [E0255]">test2</error>;
        fn <error descr="The name `test2` is defined multiple times [E0255]">test2</error>(){}

        use bar::A as <error descr="The name `Arc` is defined multiple times [E0255]">Arc</error>;
        struct <error descr="The name `Arc` is defined multiple times [E0255]">Arc</error>{}

        use bar::<error descr="The name `test3` is defined multiple times [E0252]">test3</error>;
        use baz::<error descr="The name `test3` is defined multiple times [E0252]">test3</error>;

        mod bar {
            pub struct A {}
            pub mod test3 {}
            pub fn test1() {}
        }
        mod baz {
            pub struct test3 {}
            pub const test2: u8 = 0;
        }
    """)

    fun `test no duplicates with import E0252`() = checkErrors("""
        use bar::{test1};
        use baz::test2;
        use bar::test3;
        use bar::unresolved;
        use baz::unresolved;
        use baz::test3;
        use bar::Arc as A;
        struct Arc{}
        fn test1(){}
        fn test2(){}

        mod bar{
            pub struct Arc{}
            pub mod test1{}
            pub mod test3{}
        }
        mod baz{
            pub struct test2{}
            pub const test3:u8 = 0;
        }
    """)

    fun `test no duplicates with import E0252 textual-scoped macros`() = checkDontTouchAstInOtherFiles("""
    //- main.rs
        use test_package::foo;
        macro_rules! foo { () => {} }
    //- lib.rs
        #[macro_export]
        macro_rules! foo { () => {} }
    """)

    fun `test no duplicates with import E0252 private item`() = checkErrors("""
        mod mod1 {
            fn foo() {}
            pub struct foo {}
        }
        mod mod2 {
            pub fn foo() {}
        }

        use mod1::foo;
        use mod2::foo;
    """)

    fun `test no E0252 multiple underscore aliases`() = checkErrors("""
        mod foo {
            pub trait T1 {}
        }
        mod bar {
            pub trait T2 {}
        }
        use foo::T1 as _;
        use bar::T2 as _;
    """)

    fun `test duplicates item vs import E0255`() = checkErrors("""
        mod inner {
            pub fn foo1() {}
            pub fn foo2() {}
        }

        use inner::<error descr="The name `foo1` is defined multiple times [E0255]">foo1</error>;
        fn <error descr="The name `foo1` is defined multiple times [E0255]">foo1</error>() {}

        use inner::foo2;
        struct foo2 {}
    """)

    @SkipTestWrapping
    fun `test duplicates item vs extern crate E0260`() = checkErrors("""
        <error descr="The name `foo1` is defined multiple times [E0260]">extern crate std as foo1;</error>
        struct <error descr="The name `foo1` is defined multiple times [E0260]">foo1</error> {}

        extern crate std as foo2;
        fn foo2() {}
    """)

    @SkipTestWrapping
    @ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
    fun `test E0260 respects crate aliases`() = checkErrors("""
        extern crate core as core_alias;
        mod core {}

        <error descr="The name `alloc` is defined multiple times [E0260]">extern crate alloc;</error>
        mod <error descr="The name `alloc` is defined multiple times [E0260]">alloc</error> {}
    """)

    @SkipTestWrapping
    fun `test duplicates import vs extern crate E0254`() = checkErrors("""
        mod inner {
            pub struct foo1 {}
            pub fn foo2() {}
        }

        <error descr="The name `foo1` is defined multiple times [E0254]">extern crate std as foo1;</error>
        use inner::<error descr="The name `foo1` is defined multiple times [E0254]">foo1</error>;

        extern crate std as foo2;
        use inner::foo2;
    """)

    @SkipTestWrapping
    fun `test duplicates extern crate vs extern crate E0259`() = checkErrors("""
        <error descr="The name `std` is defined multiple times [E0259]">extern crate std;</error>
        <error descr="The name `std` is defined multiple times [E0259]">extern crate core as std;</error>
    """)

    fun `test duplicate self import in use group E0430`() = checkErrors("""
        use foo::{
            <error descr="The `self` import appears more than once in the list [E0430]">self</error>,
            <error descr="The `self` import appears more than once in the list [E0430]">self</error>
        };
        use foo::{self};
        use foo::{self as foo1, self as foo2};
        use self::mod1::{self};
    """)
}
