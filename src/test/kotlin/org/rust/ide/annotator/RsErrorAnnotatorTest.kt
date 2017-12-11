/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsErrorAnnotatorTest : RsAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/errors"

    fun `test invalid module declarations`() = doTest("helper.rs")

    fun `test create file quick fix`() = checkByDirectory {
        openFileInEditor("mod.rs")
        applyQuickFix("Create module file")
    }

    fun `test create file and expand module quick fix`() = checkByDirectory {
        openFileInEditor("foo.rs")
        applyQuickFix("Create module file")
    }

    fun `test paths`() = checkErrors("""
        fn main() {
            let ok = self::super::super::foo;
            let ok = super::foo::bar;

            let _ = <error descr="Invalid path: self and super are allowed only at the beginning">::self</error>::foo;
            let _ = <error>::super</error>::foo;
            let _ = <error>self::self</error>;
            let _ = <error>super::self</error>;
            let _ = <error>foo::self</error>::bar;
            let _ = <error>self::foo::super</error>::bar;
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

    fun `test absent method in trait impl E0046`() = checkErrors("""
        trait TError {
            fn bar();
            fn baz();
            fn boo();
        }
        <error descr="Not all trait items implemented, missing: `bar`, `boo` [E0046]">impl TError for ()</error> {
            fn baz() {}
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

    fun `test incorrect params number in trait impl E0050`() = checkErrors("""
        trait T {
            fn ok_foo();
            fn ok_bar(a: u32, b: f64);
            fn foo();
            fn bar(a: u32);
            fn baz(a: u32, b: bool, c: f64);
            fn boo(&self, o: isize);
        }
        struct S;
        impl T for S {
            fn ok_foo() {}
            fn ok_bar(a: u32, b: f64) {}
            fn foo<error descr="Method `foo` has 1 parameter but the declaration in trait `T` has 0 [E0050]">(a: u32)</error> {}
            fn bar<error descr="Method `bar` has 2 parameters but the declaration in trait `T` has 1 [E0050]">(a: u32, b: bool)</error> {}
            fn baz<error descr="Method `baz` has 0 parameters but the declaration in trait `T` has 3 [E0050]">()</error> {}
            fn boo<error descr="Method `boo` has 2 parameters but the declaration in trait `T` has 1 [E0050]">(&self, o: isize, x: f16)</error> {}
        }
    """)

    fun `test invalid parameters number in variadic functions E0060`() = checkErrors("""
        extern {
            fn variadic_1(p1: u32, ...);
            fn variadic_2(p1: u32, p2: u32, ...);
        }

        fn main() {
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

    fun `test self in impl not in trait E0185`() = checkErrors("""
        trait T {
            fn ok_foo(&self, x: u32);
            fn ok_bar(&mut self);
            fn ok_baz(self);
            fn foo(x: u32);
            fn bar();
            fn baz(o: bool);
        }
        struct S;
        impl T for S {
            fn ok_foo(&self, x: u32) {}
            fn ok_bar(&mut self) {}
            fn ok_baz(self) {}
            fn foo(<error descr="Method `foo` has a `&self` declaration in the impl, but not in the trait [E0185]">&self</error>, x: u32) {}
            fn bar(<error descr="Method `bar` has a `&mut self` declaration in the impl, but not in the trait [E0185]">&mut self</error>) {}
            fn baz(<error descr="Method `baz` has a `self` declaration in the impl, but not in the trait [E0185]">self</error>, o: bool) {}
        }
    """)

    fun `test self in trait not in impl E0186`() = checkErrors("""
        trait T {
            fn ok_foo(&self, x: u32);
            fn ok_bar(&mut self);
            fn ok_baz(self);
            fn foo(&self, x: u32);
            fn bar(&mut self);
            fn baz(self, o: bool);
        }
        struct S;
        impl T for S {
            fn ok_foo(&self, x: u32) {}
            fn ok_bar(&mut self) {}
            fn ok_baz(self) {}
            fn foo<error descr="Method `foo` has a `&self` declaration in the trait, but not in the impl [E0186]">(x: u32)</error> {}
            fn bar<error descr="Method `bar` has a `&mut self` declaration in the trait, but not in the impl [E0186]">()</error> {}
            fn baz<error descr="Method `baz` has a `self` declaration in the trait, but not in the impl [E0186]">(o: bool)</error> {}
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

    fun `test unknown method in trait impl E0407`() = checkErrors("""
        trait T {
            fn foo();
        }
        impl T for () {
            fn foo() {}
            fn <error descr="Method `quux` is not a member of trait `T` [E0407]">quux</error>() {}
        }
    """)

    fun `test name duplication in param list E0415`() = checkErrors("""
        fn foo(x: u32, X: u32) {}
        fn bar<T>(T: T) {}

        fn simple(<error descr="Identifier `a` is bound more than once in this parameter list [E0415]">a</error>: u32,
                  b: bool,
                  <error>a</error>: f64) {}
        fn tuples(<error>a</error>: u8, (b, (<error>a</error>, c)): (u16, (u32, u64))) {}
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

            <error descr="Unresolved module">mod foo;</error>
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

    fun `testE0424 self in impl`() = checkErrors("""
        struct Foo;

        impl Foo {
            fn foo() {
                let a = <error descr="The self keyword was used in a static method [E0424]">self</error>;
            }
        }
    """)

    fun `test self expression outside function`() = checkErrors("""
        const C: () = <error descr="self value is not available in this context">self</error>;
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
                foo(1, 2, 3);
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

    fun `test should annotate lazy statics E0603`() = checkErrors("""
        mod m {
            lazy_static! { pub static ref BLA: () = {}; }
            lazy_static! { static ref BLOP: () = {}; }
        }

        use m::BLA;
        use <error>m::BLOP</error>;

        fn main() { }
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
}
