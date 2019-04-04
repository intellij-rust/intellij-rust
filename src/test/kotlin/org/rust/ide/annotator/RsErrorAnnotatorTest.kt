/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace

class RsErrorAnnotatorTest : RsAnnotatorTestBase(RsErrorAnnotator::class.java) {

    @MockRustcVersion("1.29.0")
    fun `test invalid module declarations`() = checkByFileTree("""
    //- main.rs
        mod module;
    //- module.rs
        /*caret*/
        <error descr="mod statements in non-mod.rs files is experimental [E0658]">mod foo;</error>

        #[path="helper.rs"]
        mod foobar;

        #[path=""] mod <error descr="File not found for module `nonono` [E0583]">nonono</error>;

        mod inner {
            mod <error descr="File not found for module `bar` [E0583]">bar</error>;
        }

        fn foo() {
            mod foo {
                <error descr="Cannot declare a non-inline module inside a block unless it has a path attribute">mod bar;</error>
            }
        }
    //- helper.rs
    """)

    fun `test create file quick fix`() = checkFixByFileTree("Create module file", """
    //- main.rs
        mod bar;
    //- bar/mod.rs
        mod <error descr="File not found for module `foo` [E0583]">/*caret*/foo</error>;

        fn main() {
            println!("Hello, World!");
        }
    """, """
    //- main.rs
        mod bar;
    //- bar/mod.rs
        mod foo;

        fn main() {
            println!("Hello, World!");
        }
    //- bar/foo.rs
    """)

    fun `test no E0583 if no semicolon after module declaration`() = checkByText("""
        mod foo<error descr="';' or '{' expected"> </error>
        // often happens during typing `mod foo {}`
    """)

    @MockRustcVersion("1.29.0")
    fun `test create file and expand module quick fix`() = checkFixByFileTree("Create module file", """
    //- main.rs
        mod foo;
    //- foo.rs
        <error descr="mod statements in non-mod.rs files is experimental [E0658]">mod bar/*caret*/;</error>
    """, """
    //- main.rs
        mod foo;
    //- foo/mod.rs
        mod bar;
    //- foo/bar.rs
    """)

    // TODO: check `Create module file` quick fix
    @MockRustcVersion("1.31.0")
    fun `test create module file`() = checkByFileTree("""
    //- main.rs
        mod foo;
    //- foo.rs
        mod <error descr="File not found for module `bar` [E0583]">/*caret*/bar</error>;
    """)

    fun `test paths`() = checkErrors("""
        fn main() {
            let ok = self::super::super::foo;
            let ok = super::foo::bar;

            let _ = ::<error descr="Invalid path: self and super are allowed only at the beginning">self</error>::foo;
            let _ = ::<error>super</error>::foo;
            let _ = self::<error>self</error>;
            let _ = super::<error>self</error>;
            let _ = foo::<error>self</error>::bar;
            let _ = self::foo::<error>super</error>::bar;
        }
    """)

    fun `test invalid chain comparison`() = checkErrors("""
        fn foo(x: i32) {
            <error descr="Chained comparison operator require parentheses">1 < x < 3</error>;
            <error descr="Chained comparison operator require parentheses">1 > x < 3</error>;
            <error descr="Chained comparison operator require parentheses">1 > x > 3</error>;
            <error descr="Chained comparison operator require parentheses">1 < x > 3</error>;
            <error descr="Chained comparison operator require parentheses">1 <= x < 3</error>;
            <error descr="Chained comparison operator require parentheses">1 < x <= 3</error>;
            <error descr="Chained comparison operator require parentheses">1 == x < 3</error>;
            <error descr="Chained comparison operator require parentheses">1 < x == 3</error>;
        }
    """)

    fun `test valid chain comparison`() = checkErrors("""
        fn foo(x: i32, y: bool) {
            let _ = 1 < x && x < 10;
            let _ = 1 < x || x < 10;
            let _ = (1 == x) == y;
            let _ = y == (1 == x);
        }
    """)

    fun `test not applied E0046`() = checkErrors("""
        trait T {
            fn foo() {}
            fn bar();
        }
        impl T for() {
            fn bar() {}
        }
    """)

    fun `test ignore macros E0046`() = checkErrors("""
        trait T { fn foo(&self); }

        macro_rules! impl_foo {
            () => { fn foo(&self) {} };
        }

        struct S;

        impl T for S { impl_foo!(); }
    """)

    fun `test invalid parameters number in variadic functions E0060`() = checkErrors("""
        extern {
            fn variadic_1(p1: u32, ...);
            fn variadic_2(p1: u32, p2: u32, ...);
        }

        unsafe fn test() {
            variadic_1<error descr="This function takes at least 1 parameter but 0 parameters were supplied [E0060]">()</error>;
            variadic_1(42);
            variadic_1(42, 43);
            variadic_2<error descr="This function takes at least 2 parameters but 0 parameters were supplied [E0060]">()</error>;
            variadic_2<error descr="This function takes at least 2 parameters but 1 parameter was supplied [E0060]">(42)</error>;
            variadic_2(42, 43);
        }
    """)

    fun `test invalid parameters number in free functions E0061`() = checkErrors("""
        fn par_0() {}
        fn par_1(p: bool) {}
        fn par_3(p1: u32, p2: f64, p3: &'static str) {}

        fn main() {
            par_0();
            par_1(true);
            par_3(12, 7.1, "cool");

            par_0<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(4)</error>;
            par_1<error descr="This function takes 1 parameter but 0 parameters were supplied [E0061]">()</error>;
            par_1<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(true, false)</error>;
            par_3<error descr="This function takes 3 parameters but 2 parameters were supplied [E0061]">(5, 1.0)</error>;
        }
    """)

    fun `test invalid parameters number in assoc function E0061`() = checkErrors("""
        struct Foo;
        impl Foo {
            fn par_0() {}
            fn par_2(p1: u32, p2: f64) {}
        }

        fn main() {
            Foo::par_0();
            Foo::par_2(12, 7.1);

            Foo::par_0<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(4)</error>;
            Foo::par_2<error descr="This function takes 2 parameters but 3 parameters were supplied [E0061]">(5, 1.0, "three")</error>;
        }
    """)

    fun `test invalid parameters number in impl methods E0061`() = checkErrors("""
        struct Foo;
        impl Foo {
            fn par_0(&self) {}
            fn par_2(&self, p1: u32, p2: f64) {}
        }

        fn main() {
            let foo = Foo;
            foo.par_0();
            foo.par_2(12, 7.1);

            foo.par_0<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(4)</error>;
            foo.par_2<error descr="This function takes 2 parameters but 3 parameters were supplied [E0061]">(5, 1.0, "three")</error>;
            foo.par_2<error descr="This function takes 2 parameters but 0 parameters were supplied [E0061]">()</error>;
        }
    """)

    fun `test invalid parameters number in tuple structs E0061`() = checkErrors("""
        struct Foo0();
        struct Foo1(u8);
        fn main() {
            let _ = Foo0();
            let _ = Foo1(1);

            let _ = Foo0<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(4)</error>;
            let _ = Foo1<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(10, false)</error>;
        }
    """)

    fun `test invalid parameters number in tuple enum variants E0061`() = checkErrors("""
        enum Foo {
            VAR0(),
            VAR1(u8)
        }
        fn main() {
            let _ = Foo::VAR0();
            let _ = Foo::VAR1(1);

            let _ = Foo::VAR0<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(4)</error>;
            let _ = Foo::VAR1<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(10, false)</error>;
        }
    """)

    fun `test respects cfg attribute E0061`() = checkErrors("""
        struct Foo;
        impl Foo {
            #[cfg(windows)]
            fn bar(&self, p1: u32) {}
            #[cfg(not(windows))]
            fn bar(&self) {}
        }
        fn main() {
            let foo = Foo;
            foo.bar(10);  // Ignore both calls
            foo.bar();
        }
    """)

    // We would like to cover such cases, but the resolve engine has some flaws at the moment,
    // so just ignore trait implementations to remove false positives
    fun `test ignores trait implementations E0061`() = checkErrors("""
        trait Foo1 { fn foo(&self); }
        trait Foo2 { fn foo(&self, a: u8); }
        struct Bar;
        impl Foo1 for Bar {
            fn foo(&self) {}
        }
        impl<T> Foo2 for Box<T> {
            fn foo(&self, a: u8) {}
        }
        type BFoo1<'a> = Box<Foo1 + 'a>;

        fn main() {
            let bar: BFoo1 = Box::new(Bar);
            bar.foo();   // Resolves to Foo2.foo() for Box<T>, though Foo1.foo() for Bar is the correct one
        }
    """)

    fun `test type-qualified UFCS path E0061`() = checkErrors("""
        struct S;
        impl S { fn foo(self) { } }
        fn main() {
            <S>::foo(S);
        }
    """)

    fun `test empty return E0069`() = checkErrors("""
        fn ok1() { return; }
        fn ok2() -> () { return; }
        fn ok3() -> u32 {
            let _ = || return;
            return 10
        }

        fn err1() -> bool {
            <error descr="`return;` in a function whose return type is not `()` [E0069]">return</error>;
        }
        fn err2() -> ! {
            <error>return</error>
        }
    """)

    @MockRustcVersion("1.33.0-nightly")
    fun `test type placeholder in signatures E0121`() = checkErrors("""
        fn ok(_: &'static str) {
            let four = |x: _| 4;
            let _ = match (8, 3) { (_, _) => four(1) };
            if let Some(_) = Some(0) {}
            let foo = || -> _ { 42 };
            let bar = || -> Option<_> { Some(1) };
            let xy : Vec<(&str, fn(_, _) -> _)> = vec![
                ("x", |a: i32, b: i32| a + b),
                ("y", |a: i32, b: i32| a - b)
            ];
        }

        fn foo(a: <error descr="The type placeholder `_` is not allowed within types on item signatures [E0121]">_</error>) {}
        fn bar() -> <error>_</error> {}
        fn baz(t: (u32, <error>_</error>)) -> (bool, (f64, <error>_</error>)) {}
        static FOO: <error>_</error> = 42;
    """)

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

    fun `test negative impls for traits E0198`() = checkErrors("""
        struct Foo;
        struct Foo2;

        trait Bar1 { }
        unsafe trait Bar2 { }

        impl !Bar1 for Foo { }
        impl !Bar2 for Foo { }
        unsafe impl !<error descr="Negative implementations are not unsafe [E0198]">Bar1</error> for Foo2 { }
        unsafe impl !<error descr="Negative implementations are not unsafe [E0198]">Bar2</error> for Foo2 { }
    """)

    fun `test only safe impls for safe traits E0199`() = checkErrors("""
        struct Foo;
        struct Foo2;

        trait Bar { }

        unsafe impl <error descr="Implementing the trait `Bar` is not unsafe [E0199]">Bar</error> for Foo { }
        impl Bar for Foo2 { }
    """)

    fun `test only unsafe impls for unsafe traits E0200`() = checkErrors("""
        struct Foo;
        struct Foo2;

        unsafe trait Bar { }

        unsafe impl Bar for Foo { }
        impl <error descr="The trait `Bar` requires an `unsafe impl` declaration [E0200]">Bar</error> for Foo2 { }
    """)

    fun `test may_dangle E0569`() = checkErrors("""
        struct Foo1<T>(T);
        struct Foo2<T>(T);
        struct Foo3<T>(T);
        struct Foo4<T>(T);

        trait Bar { }

        unsafe impl<#[may_dangle] A> Bar for Foo1<A> { }
        impl<#[may_dangle] A> <error descr="Requires an `unsafe impl` declaration due to `#[may_dangle]` attribute [E0569]">Bar</error> for Foo2<A> { }
        unsafe impl<#[may_dangle]'a, A> Bar for Foo3<A> { }
        impl<#[may_dangle]'a, A> <error descr="Requires an `unsafe impl` declaration due to `#[may_dangle]` attribute [E0569]">Bar</error> for Foo4<A> { }
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

    fun `test undeclared lifetimes E0261`() = checkErrors("""
        fn foo<'a, 'b>(x: &'a u32, f: &'b Fn(&'b u8) -> &'b str) -> &'a u32 { x }
        const FOO: for<'a> fn(&'a u32) -> &'a u32 = foo_func;
        struct Struct<'a> { s: &'a str }
        enum En<'a, 'b> { A(&'a u32), B(&'b bool) }
        type Str<'d> = &'d str;

        fn foo_err<'a>(x: &<error descr="Use of undeclared lifetime name `'b` [E0261]">'b</error> str) {}
        fn bar() {
            'foo: loop {
                let _: &<error descr="Use of undeclared lifetime name `'foo` [E0261]">'foo</error> str;
            }
        }
    """)

    fun `test not applied to static lifetimes E0261`() = checkErrors("""
        const ZERO: &'static u32 = &0;
        fn foo(a: &'static str) {}
    """)

    fun `test not applied to underscore lifetimes E0261`() = checkErrors("""
        const ZERO: &'_ u32 = &0;
        fn foo(a: &'_ str) {}
    """)

    fun `test reserved lifetime name ('static) E0262`() = checkErrors("""
        fn foo1<<error descr="`'static` is a reserved lifetime name [E0262]">'static</error>>(x: &'static str) {}
        struct Str1<<error>'static</error>> { a: &'static u32 }
        impl<<error>'static</error>> Str1<'static> {}
        enum En1<<error>'static</error>> { A(&'static str) }
        trait Tr1<<error>'static</error>> {}
    """)

    fun `test reserved lifetime name ('_) E0262`() = checkErrors("""
        fn foo2<<error descr="`'_` is a reserved lifetime name [E0262]">'_</error>>(x: &'_ str) {}
        struct Str2<<error>'_</error>> { a: &'_ u32 }
        impl<<error>'_</error>> Str2<'_> {}
        enum En2<<error>'_</error>> { A(&'_ str) }
        trait Tr2<<error>'_</error>> {}
    """)

    fun `test lifetime name duplication in generic params E0263`() = checkErrors("""
        fn foo<'a, 'b>(x: &'a str, y: &'b str) { }
        struct Str<'a, 'b> { a: &'a u32, b: &'b f64 }
        impl<'a, 'b> Str<'a, 'b> {}
        enum Direction<'a, 'b> { LEFT(&'a str), RIGHT(&'b str) }
        trait Trait<'a, 'b> {}

        fn bar<<error descr="Lifetime name `'a` declared twice in the same scope [E0263]">'a</error>, 'b, <error>'a</error>>(x: &'a str, y: &'b str) { }
        struct St<<error>'a</error>, 'b, <error>'a</error>> { a: &'a u32, b: &'b f64 }
        impl<<error>'a</error>, 'b, <error>'a</error>> Str<'a, 'b> {}
        enum Dir<<error>'a</error>, 'b, <error>'a</error>> { LEFT(&'a str), RIGHT(&'b str) }
        trait Tr<<error>'a</error>, 'b, <error>'a</error>> {}
    """)

    fun `test name duplication in generic params E0403`() = checkErrors("""
        fn sub<T, P>() {}
        struct Str<T, P> { t: T, p: P }
        impl<T, P> Str<T, P> {}
        enum Direction<T, P> { LEFT(T), RIGHT(P) }
        trait Trait<T, P> {}

        fn add<<error descr="The name `T` is already used for a type parameter in this type parameter list [E0403]">T</error>, <error>T</error>, P>() {}
        struct S< <error>T</error>, <error>T</error>, P> { t: T, p: P }
        impl<     <error>T</error>, <error>T</error>, P> S<T, T, P> {}
        enum En<  <error>T</error>, <error>T</error>, P> { LEFT(T), RIGHT(P) }
        trait Tr< <error>T</error>, <error>T</error>, P> { fn foo(t: T) -> P; }
    """)

    fun `test no E0407 for method defined with a macro`() = checkErrors("""
        macro_rules! foo {
            ($ i:ident, $ j:ty) => { fn $ i(&self) -> $ j { unimplemented!() } }
        }
        trait T {
            foo!(foo, ());
        }
        impl T for () {
            fn foo(&self) {}
        }
    """)

    fun `test name duplication in param list E0415`() = checkErrors("""
        fn foo(x: u32, X: u32) {}
        fn bar<T>(T: T) {}

        fn simple(<error descr="Identifier `a` is bound more than once in this parameter list [E0415]">a</error>: u32,
                  b: bool,
                  <error>a</error>: f64) {}
        fn tuples(<error>a</error>: u8, (b, (<error>a</error>, c)): (u16, (u32, u64))) {}
        fn fn_ptrs(x: i32, y: fn (x: i32, y: i32), z: fn (x: i32, x: i32)) {}
    """)

    fun `test undeclared label E0426`() = checkErrors("""
        fn ok() {
            'foo: loop { break 'foo }
            'bar: while true { continue 'bar }
            'baz: for _ in 0..3 { break 'baz }
            'outer: loop {
                'inner: while true { break 'outer }
            }
        }

        fn err<'a>(a: &'a str) {
            'foo: loop { continue <error descr="Use of undeclared label `'bar` [E0426]">'bar</error> }
            while true { break <error descr="Use of undeclared label `'static` [E0426]">'static</error> }
            for _ in 0..1 { break <error descr="Use of undeclared label `'a` [E0426]">'a</error> }
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

            const  <error descr="A value named `Dup` has already been defined in this block [E0428]">Dup</error>: u32 = 20;
            static <error descr="A value named `Dup` has already been defined in this block [E0428]">Dup</error>: i64 = -1.3;
            fn     <error descr="A value named `Dup` has already been defined in this block [E0428]">Dup</error>() {}
            struct <error descr="A type named `Dup` has already been defined in this block [E0428]">Dup</error>;
            trait  <error descr="A type named `Dup` has already been defined in this block [E0428]">Dup</error> {}
            enum   <error descr="A type named `Dup` has already been defined in this block [E0428]">Dup</error> {}
            mod    <error descr="A type named `Dup` has already been defined in this block [E0428]">Dup</error> {}
        }
    """)

    fun `test name duplication in enum E0428`() = checkErrors("""
        enum Directions {
            NORTH,
            <error descr="Enum variant `SOUTH` is already declared [E0428]">SOUTH</error> { distance: f64 },
            WEST,
            <error descr="Enum variant `SOUTH` is already declared [E0428]">SOUTH</error> { distance: f64 },
            EAST
        }
    """)

    fun `test name duplication in foreign mod E0428`() = checkErrors("""
        extern "C" {
            static mut UNIQUE: u16;
            fn unique();

            static mut <error descr="A value named `DUP` has already been defined in this module [E0428]">DUP</error>: u32;
            static mut <error descr="A value named `DUP` has already been defined in this module [E0428]">DUP</error>: u32;

            fn <error descr="A value named `dup` has already been defined in this module [E0428]">dup</error>();
            fn <error descr="A value named `dup` has already been defined in this module [E0428]">dup</error>();
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

        const <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>: u32 = 20;
        static <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>: i64 = -1.3;
        fn     <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>() {}
        struct <error descr="A type named `Dup` has already been defined in this module [E0428]">Dup</error>;
        trait  <error descr="A type named `Dup` has already been defined in this module [E0428]">Dup</error> {}
        enum   <error descr="A type named `Dup` has already been defined in this module [E0428]">Dup</error> {}
        mod    <error descr="A type named `Dup` has already been defined in this module [E0428]">Dup</error> {}
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

            const <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>: u32 = 20;
            static <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>: i64 = -1.3;
            fn     <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>() {}
            struct <error descr="A type named `Dup` has already been defined in this module [E0428]">Dup</error>;
            trait  <error descr="A type named `Dup` has already been defined in this module [E0428]">Dup</error> {}
            enum   <error descr="A type named `Dup` has already been defined in this module [E0428]">Dup</error> {}
            mod    <error descr="A type named `Dup` has already been defined in this module [E0428]">Dup</error> {}
        }
    """)

    fun `test name duplication in trait E0428`() = checkErrors("""
        trait T {
            type NO_DUP_T;
            const NO_DUP_C: u8;
            fn no_dup_f();

            type <error descr="A type named `DUP_T` has already been defined in this trait [E0428]">DUP_T</error>;
            type <error descr="A type named `DUP_T` has already been defined in this trait [E0428]">DUP_T</error>;

            const <error descr="A value named `DUP_C` has already been defined in this trait [E0428]">DUP_C</error>: u32;
            const <error descr="A value named `DUP_C` has already been defined in this trait [E0428]">DUP_C</error>: u32;

            fn <error descr="A value named `dup` has already been defined in this trait [E0428]">dup</error>(&self);
            fn <error descr="A value named `dup` has already been defined in this trait [E0428]">dup</error>(&self);
        }
    """)

    fun `test respects namespaces E0428`() = checkErrors("""
        mod m {
            // Consts and types are in different namespaces
            type  NO_C_DUP = bool;
            const NO_C_DUP: u32 = 10;

            // Functions and types are in different namespaces
            type NO_F_DUP = u8;
            fn   NO_F_DUP() {}

            // Consts and functions are in the same namespace (values)
            fn <error descr="A value named `DUP_V` has already been defined in this module [E0428]">DUP_V</error>() {}
            const <error>DUP_V</error>: u8 = 1;

            // Enums and traits are in the same namespace (types)
            trait <error descr="A type named `DUP_T` has already been defined in this module [E0428]">DUP_T</error> {}
            enum <error>DUP_T</error> {}

            mod <error>foo</error>;
            fn foo() {}
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

    fun `test respects cfg attribute E0428`() = checkErrors("""
        mod opt {
            #[cfg(not(windows))] mod foo {}
            #[cfg(windows)]     mod foo {}

            #[cfg(windows)] fn <error descr="A value named `hello_world` has already been defined in this module [E0428]">hello_world</error>() {}
            fn <error descr="A value named `hello_world` has already been defined in this module [E0428]">hello_world</error>() {}
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

    fun `test unnecessary pub E0449`() = checkErrors("""
        <error descr="Unnecessary visibility qualifier [E0449]">pub</error> extern "C" { }

        pub struct S {
            foo: bool,
            pub bar: u8,
            pub baz: (u32, f64)
        }
        <error>pub</error> impl S {}

        struct STuple (pub u32, f64);

        pub enum E {
            FOO {
                bar: u32,
                <error>pub</error> baz: u32
            },
            BAR(<error>pub</error> u32, f64)
        }

        pub trait Foo {
            type A;
            fn b();
            const C: u32;
        }
        struct Bar;
        <error>pub</error> impl Foo for Bar {
            <error>pub</error> type A = u32;
            <error>pub</error> fn b() {}
            <error>pub</error> const C: u32 = 10;
        }
    """)

    fun `test self in static method E0424`() = checkErrors("""
        struct Foo;

        impl Foo {
            fn foo() {
                let a = <error descr="The self keyword was used in a static method [E0424]">self</error>;
            }
        }
    """)

    fun `test self in vis restriction in static method no E0424`() = checkErrors("""
        struct Foo;

        impl Foo {
            pub(self) fn foo() {}
        }
    """)

    fun `test self expression outside function`() = checkErrors("""
        const C: () = <error descr="self value is not available in this context">self</error>;
    """)

    fun `test do not annotate 'self' in visibility restriction`() = checkErrors("""
        struct Foo {
            pub(self) attr1: bool,
            pub(in self) attr2: bool
        }
    """)

    fun `test ignore non static E0424`() = checkErrors("""
        struct Foo;

        impl Foo {
            fn foo(self) {
                let a = self;
            }
        }
    """)

    fun `test ignore module path E0424`() = checkErrors("""
        fn foo() {
        }

        fn bar() {
            self::foo()
        }
    """)

    fun `test ignore use self with parens E0424`() = checkErrors("""
        fn foo() {}
        fn bat() {}
        fn bar() {
            use self::{foo};
            use self::{foo,bat};
        }
    """)

    fun `test don't touch AST in other files`() = checkDontTouchAstInOtherFiles("""
        //- main.rs
            mod m;
            use m::*;

            fn main() {
                unsafe { foo(1, 2, 3); }
                bar<error>(92)</error>;
                let _ = S {};
            }  //^

            impl T for S { }
        //- m.rs
            extern "C" {
                pub fn foo(x: i32, ...);
            }
            pub fn bar() {}

            pub trait T {}
            pub struct S {}
        """)

    fun `test a private item was used outside of its scope E0624`() = checkErrors("""
        mod some_module {
            pub struct Foo;

            impl Foo {
                fn method(&self) {}
            }
        }
        fn main() {
            let f = some_module::Foo;
            f.<error descr="Method `method` is private [E0624]">method</error>();
        }
    """)

    fun `test should not annotate trait methods E0624`() = checkErrors("""
        mod some_module {
            pub struct Foo;
            pub trait Test {
                fn method(&self);
            }

            impl Test for Foo {
                fn method(&self) {}
            }
        }
        use some_module::Test;

        fn main() {
            let f = some_module::Foo;
            f.method();
        }
    """)

    fun `test should not annotate trait default methods E0624`() = checkErrors("""
        mod some_module {
            pub struct Foo;
            pub trait Test {
                fn bar(&self) {}
            }

            impl Test for Foo {}
        }
        use some_module::Test;
        fn main() {
            let f = some_module::Foo;
            f.bar();
        }
    """)

    fun `test should not annotate trait associated types E0603`() = checkErrors("""
        mod some_module {
            pub struct Foo;
            pub trait Test {
                type Item;
            }

            impl Test for Foo {
                type Item = Foo;
            }
        }
        use some_module::Test;
        struct C<T>;
        impl<T: Test> Test for C<T> {
            type Item = T::Item;
        }
        fn main() {}
    """)

    fun `test should not annotate public methods E0624`() = checkErrors("""
        mod some_module {
            pub struct Foo;

            impl Foo {
                pub fn method(&self) {}
            }
        }
        fn main() {
            let f = some_module::Foo;
            f.method();
        }
    """)

    fun `test should not annotate in the same module E0624`() = checkErrors("""
        struct Foo;

        impl Foo {
            fn method(&self) {}
        }

        fn main() {
            let f = some_module::Foo;
            f.method();
        }
    """)

    fun `test attempted to access a private field on a struct E0616`() = checkErrors("""
        mod some_module {
            pub struct Foo {
                x: u32,
            }

            impl Foo {
                pub fn new() -> Foo { Foo { x: 0 } }
            }
        }
        fn main() {
            let f = some_module::Foo::new();
            f.<error descr="Field `x` of struct `some_module::Foo` is private [E0616]">x</error>;
        }
    """)

    fun `test attempted to access a public field on a struct E0616`() = checkErrors("""
        mod some_module {
            pub struct Foo {
                pub x: u32,
            }

            impl Foo {
                pub fn new() -> Foo { Foo { x: 0 } }
            }
        }
        fn main() {
            let f = some_module::Foo::new();
            f.x;
        }
    """)

    fun `test should not annotate in the same module E0616`() = checkErrors("""
        struct Foo {
            x: u32,
        }

        fn main() {
            let f = Foo { x: 0 };
            f.x;
        }
    """)

    fun `test should not annotate super fields in a super module E0624`() = checkErrors("""
        mod foo {
            use S;
            fn bar() {
                let s = S::new();
                s.x;
            }
        }

        struct S {
            x: u32
        }
        impl S {
            fn new() -> S {
                S { x:1 }
            }
        }
    """)

    fun `test should not annotate super mod E0624`() = checkDontTouchAstInOtherFiles("""
        //- m/mod.rs
            use S;
            fn bar() {
                let s = S::new();
                s.x;
            }
        //- main.rs
            mod m;

            pub struct S {
                x: u32
            }
            impl S {
                pub fn new() -> S {
                    S { x:1 }
                }
            }
        """, filePath = "m/mod.rs")

    fun `test const outside scope E0603`() = checkErrors("""
        mod foo {
            const BAR: u32 = 0x_a_bad_1dea_u32;
        }

        use <error descr="Constant `foo::BAR` is private [E0603]">foo::BAR</error>;
    """)

    fun `test not const outside scope E0603`() = checkErrors("""
        mod foo {
            pub const BAR: u32 = 0x_a_bad_1dea_u32;
        }

        use foo::BAR;
    """)

    fun `test fn outside scope E0603`() = checkErrors("""
        mod foo {
            fn bar() {}
        }

        use <error descr="Function `foo::bar` is private [E0603]">foo::bar</error>;
    """)

    fun `test not fn outside scope E0603`() = checkErrors("""
        mod foo {
            pub fn bar() {}
        }

        use foo::bar;
    """)

    fun `test struct outside scope E0603`() = checkErrors("""
        mod foo {
            struct Bar;
        }

        use <error descr="Struct `foo::Bar` is private [E0603]">foo::Bar</error>;
    """)

    fun `test struct fn outside scope E0603`() = checkErrors("""
        mod foo {
            pub struct Bar;
        }

        use foo::Bar;
    """)

    fun `test module is private E0603`() = checkDontTouchAstInOtherFiles("""
        //- foo/mod.rs
            mod bar;
        //- foo/bar.rs
            pub struct Foo;
        //- main.rs
            mod foo;

            use <error descr="Module `foo::bar` is private [E0603]">foo::bar</error>::Foo;
    """)

    fun `test module is public E0603`() = checkDontTouchAstInOtherFiles("""
        //- foo/mod.rs
            pub mod bar;
        //- foo/bar.rs
            pub struct Foo;
        //- main.rs
            mod foo;

            use foo::bar::Foo;
    """)

    fun `test access to sibling not public module E0603`() = checkErrors("""
        mod foo {
            pub struct Foo;
        }
        mod bar {
            use foo::Foo;
        }
    """)

    fun `test access to sibling not public module with file structure E0603`() = checkDontTouchAstInOtherFiles("""
        //- main.rs
            mod foo;
            mod bar;
        //- foo.rs
            pub struct Foo;
        //- bar.rs
            use foo::Foo;
    """, filePath = "bar.rs")

    fun `test access to sibling module of some ancestor module E0603`() = checkErrors("""
        mod foo {
            pub struct Foo;
        }
        mod bar {
            mod baz {
                use foo::Foo;
            }
        }
    """)

    fun `test access to sibling module of some ancestor module with file structure E0603`() = checkDontTouchAstInOtherFiles("""
        //- main.rs
            mod foo;
            mod bar;
        //- foo.rs
            pub struct Foo;
        //- bar.rs
            mod baz {
                use foo::Foo;
            }
    """, filePath = "bar.rs")

    fun `test complex access to module E0603`() = checkErrors("""
        mod foo {
            mod qwe {
                pub struct Foo;
            }
        }
        mod bar {
            mod baz {
                use <error descr="Module `foo::qwe` is private [E0603]">foo::qwe</error>::Foo;
            }
        }
    """)

    fun `test complex access to module with file structure E0603`() = checkDontTouchAstInOtherFiles("""
        //- main.rs
            mod foo;
            mod bar;
        //- foo/mod.rs
            mod qwe;
        //- foo/qwe.rs
            pub struct Foo;
        //- bar/mod.rs
            mod baz;
        //- bar/baz.rs
            use <error descr="Module `foo::qwe` is private [E0603]">foo::qwe</error>::Foo;
    """, filePath = "bar/baz.rs")

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test item with crate visibility is NOT visible from other crates E0603`() = checkByFileTree("""
        //- lib.rs
            #![feature(crate_visibility_modifier)]
            crate fn foo() {}
            pub(crate) fn bar() {}
        //- main.rs
            extern crate test_package;
            use <error descr="Function `test_package::foo` is private [E0603]">test_package::foo</error>; /*caret*/
            use <error descr="Function `test_package::bar` is private [E0603]">test_package::bar</error>; /*caret*/
    """, checkWarn = false)

    fun `test item with crate visibility is visible in the same crate E0603`() = checkErrors("""
        #![feature(crate_visibility_modifier)]
        mod foo {
            crate fn spam() {}
            pub(crate) fn eggs() {}
        }
        mod bar {
            use foo::spam;
            use foo::eggs;
        }
    """)

    fun `test restricted visibility E0603`() = checkErrors("""
        mod foo {
            pub mod bar {
                pub(self) fn quux() {}
                pub(super) fn spam() {}
                pub(in foo) fn eggs() {}
            }
            use <error descr="Function `self::bar::quux` is private [E0603]">self::bar::quux</error>;
            use self::bar::spam;
            use self::bar::eggs;
        }
        use <error descr="Function `foo::bar::quux` is private [E0603]">foo::bar::quux</error>;
        use <error descr="Function `foo::bar::spam` is private [E0603]">foo::bar::spam</error>;
        use <error descr="Function `foo::bar::eggs` is private [E0603]">foo::bar::eggs</error>;
    """)

    fun `test function args should implement Sized trait E0277`() = checkErrors("""
        fn foo1(bar: <error descr="the trait bound `[u8]: std::marker::Sized` is not satisfied [E0277]">[u8]</error>) {}
        fn foo2(bar: i32) {}
    """)

    fun `test function return type should implement Sized trait E0277`() = checkErrors("""
        fn foo1() -> <error descr="the trait bound `[u8]: std::marker::Sized` is not satisfied [E0277]">[u8]</error> { unimplemented!() }
        fn foo2() -> i32 { unimplemented!() }
    """)

    fun `test trait method can have arg with Self type E0277`() = checkErrors("""
        trait Foo {
            fn foo(x: Self);
        }
    """)

    fun `test trait method can return Self type E0277`() = checkErrors("""
        trait Foo {
            fn foo() -> Self;
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test Self type inside trait is sized if have Sized bound E0277`() = checkErrors("""
        trait Foo: Sized {
            fn foo() -> (Self, Self);
        }
        trait Bar where Self: Sized {
            fn foo() -> (Self, Self);
        }
        // TODO
//        trait Baz {
//            fn foo() -> (Self, Self) where Self: Sized;
//        }
    """)

    @MockRustcVersion("1.27.1")
    fun `test crate visibility feature E0658`() = checkErrors("""
        <error descr="`crate` visibility modifier is experimental [E0658]">crate</error> struct Foo;
    """)

    @MockRustcVersion("1.29.0-nightly")
    fun `test crate visibility feature E0658 2`() = checkErrors("""
        <error descr="`crate` visibility modifier is experimental [E0658]">crate</error> struct Foo;
    """)

    @MockRustcVersion("1.29.0-nightly")
    fun `test crate visibility feature E0658 3`() = checkErrors("""
        #![feature(crate_visibility_modifier)]

        crate struct Foo;
    """)

    @MockRustcVersion("1.29.0-nightly")
    fun `test crate visibility feature E0658 4`() = checkErrors("""
        crate struct Foo;

        mod foo {
            #![feature(crate_visibility_modifier)]
        }
    """)

    fun `test parenthesized lifetime bounds`() = checkErrors("""
        fn foo<'a, T: <error descr="Parenthesized lifetime bounds are not supported">('a)</error>>(t: T) {
            unimplemented!();
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test crate keyword not at the beginning E0433`() = checkErrors("""
        use crate::foo::<error descr="`crate` in paths can only be used in start position [E0433]">crate</error>::Foo;
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test crate keyword not at the beginning in use group E0433`() = checkErrors("""
        use crate::foo::{<error descr="`crate` in paths can only be used in start position [E0433]">crate</error>::Foo};
    """)

    @MockRustcVersion("1.28.0")
    fun `test crate in path feature E0658`() = checkErrors("""
        mod foo {
            pub struct Foo;
        }

        use <error descr="`crate` in paths is experimental [E0658]">crate</error>::foo::Foo;
    """)

    @MockRustcVersion("1.29.0-nightly")
    fun `test crate in path feature E0658 2`() = checkErrors("""
        mod foo {
            pub struct Foo;
        }

        use <error descr="`crate` in paths is experimental [E0658]">crate</error>::foo::Foo;
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test crate in path feature E0658 3`() = checkErrors("""
        mod foo {
            pub struct Foo;
        }

        use crate::foo::Foo;
    """)

    @MockRustcVersion("1.30.0")
    fun `test crate in path feature E0658 4`() = checkErrors("""
        mod foo {
            pub struct Foo;
        }

        use crate::foo::Foo;
    """)

    @MockRustcVersion("1.28.0")
    fun `test crate visibility restriction`() = checkErrors("""
        pub(crate) fn foo() {}
    """)

    fun `test E0404 expected trait`() = checkErrors("""
        struct S;
        enum E {}
        type T = S;
        mod a {}
        trait Trait {}
        impl <error descr="Expected trait, found struct `S` [E0404]">S</error> for S {}
        impl <error descr="Expected trait, found enum `E` [E0404]">E</error> for S {}
        impl <error descr="Expected trait, found type alias `T` [E0404]">T</error> for S {}
        impl <error descr="Expected trait, found module `a` [E0404]">a</error> for S {}
        fn foo<A: <error descr="Expected trait, found struct `S` [E0404]">S</error>>() {}
        impl Trait for S {}
    """)

    @MockRustcVersion("1.21.0")
    fun `test yield syntax feature E0658 1`() = checkErrors("""
        fn main() {
            let mut generator = || {
                <error descr="`yield` syntax is experimental [E0658]">yield</error> 1;
                return "foo"
            };
        }
    """)

    @MockRustcVersion("1.21.0-nightly")
    fun `test yield syntax feature E0658 2`() = checkErrors("""
        #![feature(generators)]

        fn main() {
            let mut generator = || {
                yield 1;
                return "foo"
            };
        }
    """)

    @MockRustcVersion("1.0.0")
    fun `test box expression feature E0658 1`() = checkErrors("""
        struct S;
        fn main() {
            let x = <error descr="`box` expression syntax is experimental [E0658]">box</error> S;
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test box expression feature E0658 2`() = checkErrors("""
        #![feature(box_syntax)]

        struct S;
        fn main() {
            let x = box S;
        }
    """)

    @MockRustcVersion("1.0.0")
    fun `test box pattern feature E0658 1`() = checkErrors("""
        struct S { x: Box<i32> }
        fn main() {
            let s = Box::new(S { x: Box::new(0) });
            let <error descr="`box` pattern syntax is experimental [E0658]">box</error> x = s;
            let S { <error descr="`box` pattern syntax is experimental [E0658]">box</error> x } = x;
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test box pattern feature E0658 2`() = checkErrors("""
        #![feature(box_patterns)]

        struct S { x: Box<i32> }
        fn main() {
            let s = Box::new(S { x: Box::new(0) });
            let box x = s;
            let S { box x } = x;
        }
    """)

    @MockRustcVersion("1.31.0")
    fun `test irrefutable let pattern E0658 1`() = checkErrors("""
        fn main() {
            if let <error descr="irrefutable let pattern is experimental [E0658]">x</error> = 42 {}
            while let <error descr="irrefutable let pattern is experimental [E0658]">x</error> = 42 {}
        }
    """)

    @MockRustcVersion("1.32.0-nightly")
    fun `test irrefutable let pattern E0658 2`() = checkErrors("""
        fn main() {
            if let <error descr="irrefutable let pattern is experimental [E0658]">x</error> = 42 {}
            while let <error descr="irrefutable let pattern is experimental [E0658]">x</error> = 42 {}
        }
    """)

    @MockRustcVersion("1.32.0-nightly")
    fun `test irrefutable let pattern E0658 3`() = checkErrors("""
        #![feature(irrefutable_let_patterns)]
        fn main() {
            if let x = 42 {}
            while let x = 42 {}
        }
    """)

    @MockRustcVersion("1.33.0-nightly")
    fun `test irrefutable let pattern E0658 4`() = checkErrors("""
        fn main() {
            if let x = 42 {}
            while let x = 42 {}
        }
    """)

    @MockRustcVersion("1.31.0")
    fun `test irrefutable let pattern E0658 for struct literals 1`() = checkErrors("""
        struct S { x: i32 }
        struct ST(i32);
        enum E1 { V { x: i32 } }
        enum E2 { A, V { x: i32 } }
        fn foo(a: S, b: ST, c: E1, d: E2, e: Unknown) {
            if let <error descr="irrefutable let pattern is experimental [E0658]">S { x }</error> = a {}
            if let <error descr="irrefutable let pattern is experimental [E0658]">S(x)</error> = b {}
            if let <error descr="irrefutable let pattern is experimental [E0658]">E1::V { x }</error> = c {}
            if let E2::V { x } = d {}
            if let Unknown { x } = e {}
        }
    """)

    @MockRustcVersion("1.32.0-nightly")
    fun `test irrefutable let pattern E0658 for struct literals 2`() = checkErrors("""
        #![feature(irrefutable_let_patterns)]
        struct S { x: i32 }
        struct ST(i32);
        enum E1 { V { x: i32 } }
        enum E2 { A, V { x: i32 } }
        fn foo(a: S, b: ST, c: E1, d: E2, e: Unknown) {
            if let S { x } = a {}
            if let S(x) = b {}
            if let E1::V { x } = c {}
            if let E2::V { x } = d {}
            if let Unknown { x } = e {}
        }
    """)

    /** Issue [#3410](https://github.com/intellij-rust/intellij-rust/issues/3410) */
    @MockRustcVersion("1.32.0")
    fun `test const pattern is not irrefutable`() = checkErrors("""
        #[derive(PartialEq, Eq)]
        struct S(usize);
        const A: S = S(1);

        fn main() {
            if let A = S(2) {}
        }
    """)

    @MockRustcVersion("1.32.0")
    fun `test if while or patterns 1`() = checkErrors("""
        enum V { V1(i32), V2(i32) }
        fn foo(y: V) {
            if let <error descr="multiple patterns in `if let` and `while let` are unstable [E0658]">V::V1(x) | V::V2(x)</error> = y {}
        }
    """)

    @MockRustcVersion("1.32.0-nightly")
    fun `test if while or patterns 2`() = checkErrors("""
        #![feature(if_while_or_patterns)]
        enum V { V1(i32), V2(i32) }
        fn foo(y: V) {
            while let V::V1(x) | V::V2(x) = y {}
        }
    """)

    @MockRustcVersion("1.33.0")
    fun `test extern_crate_self 1`() = checkErrors("""
        <error descr="`extern crate self` is experimental [E0658]">extern crate self as foo;</error>
    """)

    @MockRustcVersion("1.33.0-nightly")
    fun `test extern_crate_self 2`() = checkErrors("""
        #![feature(extern_crate_self)]

        extern crate self as foo;
    """)

    @MockRustcVersion("1.33.0-nightly")
    fun `test extern_crate_self without alias`() = checkErrors("""
        #![feature(extern_crate_self)]
        
        <error descr="`extern crate self` requires `as name`">extern crate self;</error>
    """)

    fun `test expected function E0618`() = checkErrors("""
        struct S1;
        struct S2();
        enum E { V1, V2(), V3 {} }
        fn main() {
            <error descr="Expected function, found `S1` [E0618]">S1</error>();
            S2();
            <error>E::V1</error>();
            E::V2();
            <error>E::V3</error>();
        }
    """)
}
