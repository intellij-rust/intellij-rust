/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.psi.RsDebuggerExpressionCodeFragment
import org.rust.lang.core.psi.RsExpressionCodeFragment

class RsErrorAnnotatorTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {

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

    fun `test create file quick fix 1`() = checkFixByFileTree("Create module file `foo.rs`", """
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
    """, preview = null)

    @MockRustcVersion("1.30.0")
    fun `test create file quick fix 2`() = checkFixByFileTree("Create module file `foo.rs`", """
    //- main.rs
        mod bar;
    //- bar.rs
        mod <error descr="File not found for module `foo` [E0583]">/*caret*/foo</error>;

        fn main() {
            println!("Hello, World!");
        }
    """, """
    //- main.rs
        mod bar;
    //- bar.rs
        mod foo;

        fn main() {
            println!("Hello, World!");
        }
    //- bar/foo.rs
    """, preview = null)

    fun `test create file in subdirectory quick fix 1`() = checkFixByFileTree("Create module file `foo/mod.rs`", """
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
    //- bar/foo/mod.rs
    """, preview = null)

    @MockRustcVersion("1.30.0")
    fun `test create file in subdirectory quick fix 2`() = checkFixByFileTree("Create module file `foo/mod.rs`", """
    //- main.rs
        mod bar;
    //- bar.rs
        mod <error descr="File not found for module `foo` [E0583]">/*caret*/foo</error>;

        fn main() {
            println!("Hello, World!");
        }
    """, """
    //- main.rs
        mod bar;
    //- bar.rs
        mod foo;

        fn main() {
            println!("Hello, World!");
        }
    //- bar/foo/mod.rs
    """, preview = null)

    fun `test create file in existing subdirectory quick fix`() = checkFixByFileTree("Create module file `bar/mod.rs`", """
    //- main.rs
        mod <error descr="File not found for module `bar` [E0583]">/*caret*/bar</error>;
    //- bar/some_random_file_to_create_a_directory.txt
    """, """
    //- main.rs
        mod bar;
    //- bar/mod.rs
    //- bar/some_random_file_to_create_a_directory.txt
    """, preview = null)

    fun `test no E0583 if no semicolon after module declaration`() = checkByText("""
        mod foo<error descr="';' or '{' expected"> </error>
        // often happens during typing `mod foo {}`
    """)

    @MockRustcVersion("1.29.0")
    fun `test create file and expand module quick fix`() = checkFixByFileTree("Create module file `bar.rs`", """
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
    """, preview = null)

    @MockRustcVersion("1.29.0")
    fun `test create file in subdirectory and expand module quick fix`() = checkFixByFileTree("Create module file `bar/mod.rs`", """
    //- main.rs
        mod foo;
    //- foo.rs
        <error descr="mod statements in non-mod.rs files is experimental [E0658]">mod bar/*caret*/;</error>
    """, """
    //- main.rs
        mod foo;
    //- foo/mod.rs
        mod bar;
    //- foo/bar/mod.rs
    """, preview = null)

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

    fun `test invalid parameters number in closures E0057`() = checkErrors("""
        fn main() {
            let closure_0 = || ();
            let closure_1 = |x| x;
            let closure_2 = |x, y| (x, y);

            closure_0();
            closure_0(<error descr="This function takes 0 parameters but 1 parameter was supplied [E0057]">42</error>);
            closure_1(<error descr="This function takes 1 parameter but 0 parameters were supplied [E0057]">)</error>;
            closure_1(42);
            closure_2(<error descr="This function takes 2 parameters but 0 parameters were supplied [E0057]">)</error>;
            closure_2(42<error descr="This function takes 2 parameters but 1 parameter was supplied [E0057]">)</error>;
            closure_2(42, 43);
        }
    """)

    fun `test invalid parameters number in variadic functions E0060`() = checkErrors("""
        extern {
            fn variadic_1(p1: u32, ...);
            fn variadic_2(p1: u32, p2: u32, ...);
        }

        unsafe fn test() {
            variadic_1(<error descr="This function takes at least 1 parameter but 0 parameters were supplied [E0060]">)</error>;
            variadic_1(42);
            variadic_1(42, 43);
            variadic_2(<error descr="This function takes at least 2 parameters but 0 parameters were supplied [E0060]">)</error>;
            variadic_2(42<error descr="This function takes at least 2 parameters but 1 parameter was supplied [E0060]">)</error>;
            variadic_2(42, 43);
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test invalid parameters number in free functions E0061`() = checkErrors("""
        fn par_0() {}
        fn par_1(p: bool) {}
        fn par_3(p1: u32, p2: f64, p3: &'static str) {}
        fn par_0_cfg(#[cfg(not(intellij_rust))] p1: u32) {}
        fn par_1_cfg(#[cfg(intellij_rust)] p1: u32, #[cfg(not(intellij_rust))] p1: i32) {}

        fn main() {
            par_0();
            par_1(true);
            par_3(12, 7.1, "cool");
            par_0_cfg();
            par_1_cfg(1);

            par_0(<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">4</error>);
            par_1(<error descr="This function takes 1 parameter but 0 parameters were supplied [E0061]">)</error>;
            par_1(true, <error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">false</error>);
            par_3(5, 1.0<error descr="This function takes 3 parameters but 2 parameters were supplied [E0061]">)</error>;
            par_0_cfg(<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">4</error>);
            par_1_cfg(<error descr="This function takes 1 parameter but 0 parameters were supplied [E0061]">)</error>;
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

            Foo::par_0(<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">4</error>);
            Foo::par_2(5, 1.0, <error descr="This function takes 2 parameters but 3 parameters were supplied [E0061]">"three"</error>);
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

            foo.par_0(<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">4</error>);
            foo.par_2(5, 1.0, <error descr="This function takes 2 parameters but 3 parameters were supplied [E0061]">"three"</error>);
            foo.par_2(<error descr="This function takes 2 parameters but 0 parameters were supplied [E0061]">)</error>;
        }
    """)

    fun `test invalid parameters number in tuple structs E0061`() = checkErrors("""
        struct Foo0();
        struct Foo1(u8);
        fn main() {
            let _ = Foo0();
            let _ = Foo1(1);

            let _ = Foo0(<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">4</error>);
            let _ = Foo1(10, <error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">false</error>);
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

            let _ = Foo::VAR0(<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">4</error>);
            let _ = Foo::VAR1(10, <error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">false</error>);
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
            foo.bar(<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">10</error>);
            foo.bar();
        }
    """)

    fun `test function pointers E0061`() = checkErrors("""
        fn foo() {}
        fn main() {
            let a: fn() = || {};
            let b: fn() = foo;

            a(/*error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]"*/1/*error**/);
            b(/*error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]"*/1/*error**/);
            a();
            b();
        }
    """)

    fun `test function reference E0061`() = checkErrors("""
        fn foo() {}
        fn main() {
            let a = &foo;
            let b = &a;

            a(/*error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]"*/1/*error**/);
            b(/*error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]"*/1/*error**/);
            a();
            b();
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test boxed function E0061`() = checkErrors("""
        use std::boxed::Box;

        fn foo() {}
        fn main() {
            let a = Box::new(foo);
            a(/*error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]"*/1/*error**/);
            a();
        }
    """)

    fun `test non-path call expression E0061`() = checkErrors("""
        fn foo() {}
        fn main() {
            let a: fn() = foo;
            let b: fn() = foo;

            (if 1 + 1 == 2 { a } else { b })(/*error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]"*/1/*error**/);
            (if 1 + 1 == 2 { a } else { b })();
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test trait implementations E0061`() = checkErrors("""
        trait Foo1 { fn foo(&self); }
        trait Foo2 { fn foo(&self, a: u8); }
        struct Bar;
        impl Foo1 for Bar {
            fn foo(&self) {}
        }
        impl<T> Foo2 for Box<T> {
            fn foo(&self, a: u8) {}
        }
        type BFoo1<'a> = Box<dyn Foo1 + 'a>;

        fn main() {
            let bar: BFoo1 = Box::new(Bar);
            bar.foo();
            bar.foo(/*error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]"*/1/*error**/);
        }
    """)

    fun `test trait implementation E0061`() = checkErrors("""
        trait Foo { fn foo(&self); }
        struct Bar;
        impl Foo for Bar {
            fn foo(&self) {}
        }

        fn main() {
            let bar = Bar {};
            bar.foo();
            bar.foo(/*error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]"*/1/*error**/);

            Foo::foo(&bar);
            Foo::foo(&bar, /*error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]"*/1/*error**/);

            Bar::foo(&bar);
            Bar::foo(&bar, /*error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]"*/1/*error**/);
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
        fn ok4() -> u32 {
            let _ = async { return; };
            return 10
        }

        fn err1() -> bool {
            <error descr="`return;` in a function whose return type is not `()` [E0069]">return</error>;
        }
        fn err2() -> ! {
            <error>return</error>
        }
    """)

    fun `test empty return in a function returning normalizable associated type E0069`() = checkErrors("""
        struct Struct1;
        struct Struct2;
        trait Trait { type Item; }
        impl Trait for Struct1 { type Item = (); }
        impl Trait for Struct2 { type Item = bool; }

        fn ok1() -> <Struct1 as Trait>::Item { return; }

        fn err1() -> <Struct2 as Trait>::Item {
            <error descr="`return;` in a function whose return type is not `()` [E0069]">return</error>;
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
        fn baz(t: (u32, <error>_</error>)) -> (bool, (f64, <error>_</error>)) { unreachable!() }
        static FOO: <error>_</error> = 42;
        struct Baz(i32, <error>_</error>);
        struct S {
            a: <error>_</error>
        }
        enum E {
            V1(<error>_</error>),
            V2 { a: <error>_</error> }
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

    fun `test undeclared lifetimes E0261`() = checkErrors("""
        fn foo<'a, 'b>(x: &'a u32, f: &'b Fn(&'b u8) -> &'b str) -> &'a u32 { x }
        const FOO: for<'a> fn(&'a u32) -> &'a u32 = foo_func;
        struct Struct<'a> { s: &'a str }
        struct Trait<'a, T: 'a> {}
        enum En<'a, 'b> { A(&'a u32), B(&'b bool) }
        type Str<'d> = &'d str;

        struct StructErr(&<error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error> str);
        trait TraitErr<T: <error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error>> {}
        enum EnErr<T: <error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error>> {}

        fn foo_err1(x: &<error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error> str) {}
        fn foo_err2<'a>(x: &<error descr="Use of undeclared lifetime name `'b` [E0261]">'b</error> str) {}
        fn foo_err3<T: <error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error>>(x: &<error descr="Use of undeclared lifetime name `'b` [E0261]">'b</error> str)
            where <error descr="Use of undeclared lifetime name `'c` [E0261]">'c</error>: <error descr="Use of undeclared lifetime name `'d` [E0261]">'d</error> {}
        fn bar() {
            let x: &<error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error> str = unimplemented!();
            'foo: loop {
                let _: &<error descr="Use of undeclared lifetime name `'foo` [E0261]">'foo</error> str;
            }
        }
    """)

    fun `test undeclared lifetime E0261 in impl 1`() = checkErrors("""
        trait T<'a> {}
        struct S<'a>(&'a str);
        impl T<<error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error>> for S<<error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error>> {}
    """)

    fun `test undeclared lifetime E0261 in impl 2`() = checkErrors("""
        trait T<'a> {}
        struct S<'a>(&'a str);
        impl <'b> T<<error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error>> for S<<error descr="Use of undeclared lifetime name `'a` [E0261]">'a</error>> {}
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
            while true { break <error descr="Invalid label name `'static`"><error descr="Use of undeclared label `'static` [E0426]">'static</error></error> }
            for _ in 0..1 { break <error descr="Use of undeclared label `'a` [E0426]">'a</error> }
        }
    """)

    fun `test unreachable label E0767`() = checkErrors("""
        fn ok() {
            'foo: loop { {loop {continue 'foo} }}
        }

        fn err<'a>(a: &'a str) {
            'foo: loop { || {loop {continue <error descr="Use of unreachable label `'foo` [E0767]">'foo</error>}} }
            'foo: loop { fn f() {loop {continue <error descr="Use of unreachable label `'foo` [E0767]">'foo</error>}} }
            'foo: loop { async {loop {continue <error descr="Use of unreachable label `'foo` [E0767]">'foo</error>}} }
            const a: i32 = if true {
                loop { break <error descr="Use of undeclared label `'a` [E0426]">'a</error>; }
                1
            } else {
                2
            };
            static b: i32 = if true {
                loop { break <error descr="Use of undeclared label `'a` [E0426]">'a</error>; }
                1
            } else {
                2
            };
        }
    """)

    fun `test self import not in use group E0429`() = checkErrors("""
        mod test {
            use <error descr="`self` imports are only allowed within a { } list [E0429]">self</error>;
            use <error descr="`self` imports are only allowed within a { } list [E0429]">self</error> as a;
            use crate::foo::<error descr="`self` imports are only allowed within a { } list [E0429]">self</error>;
            use ::foo::<error descr="`self` imports are only allowed within a { } list [E0429]">self</error>;
            use crate::foo::{self};
            use crate::foo::<error descr="Invalid path: self and super are allowed only at the beginning">self</error>::{a, b};
            use crate::foo::{a, self};
            use crate::foo::{a, b::self};
            use crate::foo::b::<error descr="`self` imports are only allowed within a { } list [E0429]">self</error>;
        }
    """)

    fun `test self import in use group with empty prefix E0431`() = checkErrors("""
        use {<error descr="`self` import can only appear in an import list with a non-empty prefix [E0431]">self</error>};
        use {<error descr="`self` import can only appear in an import list with a non-empty prefix [E0431]">self</error> as a};
        use foo::{self};
        use foo::{{self}};
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
            <error>pub</error> BAR(<error>pub</error> u32, f64),
            <error>pub(crate)</error> BAZ
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
            /// [link]: self
            fn bar() {}
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

        /// [link]: self
        struct Struct {}
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
        fn func1() {}
        fn func2() {}
        fn func3() {}
        fn main() {
            use self::{func1};
            use self::{func2, func3};
        }
    """)

    fun `test don't touch AST in other files`() = checkDontTouchAstInOtherFiles("""
        //- main.rs
            mod m;
            use m::*;

            fn main() {
                unsafe { foo(1, 2, 3); }
                bar(<error>92</error>);
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

    fun `test attempted to construct struct which has a private field E0451`() = checkErrors("""
        mod some_module {
            pub struct Foo {
                x: u32,
            }
        }
        fn main() {
            let f = some_module::Foo { <error descr="Field `x` of struct `some_module::Foo` is private [E0451]">x</error>: 0 };
        }
    """)

    fun `test attempted to construct struct which has a private field with field shorthand E0451`() = checkErrors("""
        mod some_module {
            pub struct Foo {
                x: u32,
            }
        }
        fn main() {
            let x: u32 = 0;
            let f = some_module::Foo { <error descr="Field `x` of struct `some_module::Foo` is private [E0451]">x</error> };
        }
    """)

    fun `test construct pub enum (fields in pub enum are public by default)`() = checkErrors("""
        mod some_module {
            pub enum Foo {
                Foo1 { x: u32 },
            }
        }
        fn main() {
            let foo = some_module::Foo::Foo1 { x: 1 };
            let some_module::Foo::Foo1 { x } = foo;
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

        use foo::<error descr="Constant `BAR` is private [E0603]">BAR</error>;
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

        use foo::<error descr="Function `bar` is private [E0603]">bar</error>;
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

        use foo::<error descr="Struct `Bar` is private [E0603]">Bar</error>;
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

            use foo::<error descr="Module `bar` is private [E0603]">bar</error>::Foo;
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
                use crate::foo::<error descr="Module `qwe` is private [E0603]">qwe</error>::Foo;
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
            use crate::foo::<error descr="Module `qwe` is private [E0603]">qwe</error>::Foo;
    """, filePath = "bar/baz.rs")

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test item with crate visibility is NOT visible from other crates E0603`() = checkByFileTree("""
        //- lib.rs
            #![feature(crate_visibility_modifier)]
            crate fn foo() {}
            pub(crate) fn bar() {}
        //- main.rs
            extern crate test_package;
            use test_package::<error descr="Function `foo` is private [E0603]">foo</error>; /*caret*/
            use test_package::<error descr="Function `bar` is private [E0603]">bar</error>;
    """, checkWarn = false)

    fun `test restricted visibility E0603`() = checkErrors("""
        mod foo {
            pub mod bar {
                pub(self) fn quux() {}
                pub(super) fn spam() {}
                pub(in foo) fn eggs() {}
            }
            use self::bar::<error descr="Function `quux` is private [E0603]">quux</error>;
            use self::bar::spam;
            use self::bar::eggs;
        }
        use foo::bar::<error descr="Function `quux` is private [E0603]">quux</error>;
        use foo::bar::<error descr="Function `spam` is private [E0603]">spam</error>;
        use foo::bar::<error descr="Function `eggs` is private [E0603]">eggs</error>;
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/3558
    fun `test no E0603 for 'pub(in self)' restricted module`() = checkErrors("""
        pub(self) mod foo {}
        use self::foo as bar;
    """)

    fun `test no E0603 for trait with impl in a child mod`() = checkErrors("""
        trait T { fn foo(&self); }
        struct S;
        mod a {
            use super::*;
            impl T for S { fn foo(&self) {} }
        }
        fn main() {
            S.foo();
        }
    """)

    fun `test E0603 when access member of trait with restricted visibility`() = checkErrors("""
        mod foo {
            pub(in foo) trait Bar { fn baz(&self); }
        }
        fn quux(a: &foo::<error descr="Trait `Bar` is private [E0603]">Bar</error>) {
            a.<error descr="Method `baz` is private [E0624]">baz</error>();
        }
    """)

    fun `test restricted visibility E0742`() = checkErrors("""
        mod mod1 {
            pub(in crate::mod1) struct S1;
            pub(in super::mod1) struct S2;
            pub(in <error descr="Visibilities can only be restricted to ancestor modules [E0742]">crate::mod2</error>) struct S3;
            pub(in <error descr="Visibilities can only be restricted to ancestor modules [E0742]">super::mod2</error>) struct S4;
            pub(in <error descr="Visibilities can only be restricted to ancestor modules [E0742]">self::mod3</error>) struct S5;
            mod mod3 {}
        }
        pub mod mod2 {}

        pub(in <error descr="Visibilities can only be restricted to ancestor modules [E0742]">crate::mod2</error>) struct S;
    """)

    fun `test function args should implement Sized trait E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        fn foo1(bar: <error descr="the trait bound `[u8]: std::marker::Sized` is not satisfied [E0277]">[u8]</error>) {}
        fn foo2(bar: i32) {}

        trait Trait { type Item; }
        struct StructSized;
        impl Trait for StructSized { type Item = i32; }
        struct StructUnsized;
        impl Trait for StructUnsized { type Item = [i32]; }

        fn foo3(bar: <error><StructUnsized as Trait>::Item</error>) {}
        fn foo4(bar: <StructSized as Trait>::Item) {}
    """)

    fun `test function return type should implement Sized trait E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        fn foo1() -> <error descr="the trait bound `[u8]: std::marker::Sized` is not satisfied [E0277]">[u8]</error> { unimplemented!() }
        fn foo2() -> i32 { unimplemented!() }

        trait Trait { type Item; }
        struct StructSized;
        impl Trait for StructSized { type Item = i32; }
        struct StructUnsized;
        impl Trait for StructUnsized { type Item = [i32]; }

        fn foo3() -> <error><StructUnsized as Trait>::Item</error> { unimplemented!() }
        fn foo4() -> <StructSized as Trait>::Item { unimplemented!() }
    """)

    fun `test type parameter with Sized bound on member function is Sized E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        struct Foo<T>(T);
        impl<T: ?Sized> Foo<T> {
            fn foo() -> T where T: Sized { unimplemented!() }
        }
        impl<T: ?Sized> Foo<T> {
            fn foo() -> T where T: Sized { unimplemented!() }
        }
    """)

    fun `test trait method without body can have arg with 'qSized' type E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        trait Foo {
            fn foo(x: Self);
            fn bar(x: <error>Self</error>) {}
            fn foobar() -> Self;
            fn baz() -> <error>Self</error> { unimplemented!() }
            fn spam<T: ?Sized>(a: T);
            fn eggs<T: ?Sized>(a: <error>T</error>) {}
            fn quux<T>(a: [T]);
            fn quuux<T>(a: <error>[T]</error>) {}
        }
    """)

    fun `test Self type inside trait is sized if have Sized bound E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        trait Foo: Sized {
            fn foo() -> (Self, Self) { unimplemented!() }
        }
        trait Bar where Self: Sized {
            fn foo() -> (Self, Self) { unimplemented!() }
        }
        trait Baz {
            fn foo() -> (Self, Self) where Self: Sized { unimplemented!() }
        }
    """)

    @MockRustcVersion("1.70.0")
    fun `test generic associated type is sized E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        pub trait Deref { type Target: ?Sized; }
        pub struct Rc<T>(T);
        impl<T> Deref for Rc<T> { type Target = T; }

        trait PointerFamily {
            type Pointer<T>: Deref<Target = T>;
        }
        struct RcFamily;
        impl PointerFamily for RcFamily {
            type Pointer<T> = Rc<T>;
        }
        fn foo<T: PointerFamily>() -> T::Pointer<i32> { // No error here
            todo!()
        }
    """)

    fun `test supertrait is not implemented E0277 simple trait`() = checkErrors("""
        trait A {}
        trait B: A {}

        struct S;

        impl <error descr="the trait bound `S: A` is not satisfied [E0277]">B</error> for S {}
    """)

    fun `test supertrait is not implemented E0277 multiple traits`() = checkErrors("""
        trait A {}
        trait B {}

        trait C: A + B {}

        struct S;

        impl <error descr="the trait bound `S: A` is not satisfied [E0277]"><error descr="the trait bound `S: B` is not satisfied [E0277]">C</error></error> for S {}
    """)

    fun `test supertrait is not implemented E0277 generic supertrait`() = checkErrors("""
        trait A<T> {}
        trait B: A<u32> {}
        trait C<T>: A<T> {}

        struct S1;
        impl <error descr="the trait bound `S1: A<u32>` is not satisfied [E0277]">B</error> for S1 {}

        struct S2;
        impl A<bool> for S2 {}
        impl <error descr="the trait bound `S2: A<u32>` is not satisfied [E0277]">B</error> for S2 {}

        struct S3;
        impl A<bool> for S3 {}
        impl A<u32> for S3 {}
        impl B for S3 {}

        struct S4;
        impl<T> <error descr="the trait bound `S4: A<T>` is not satisfied [E0277]">C<T></error> for S4 {}

        struct S5;
        impl A<u32> for S5 {}
        impl<T> <error descr="the trait bound `S5: A<T>` is not satisfied [E0277]">C<T></error> for S5 {}

        struct S6;
        impl<T> A<T> for S6 {}
        impl<T> C<T> for S6 {}

        struct S7<T>(T);
        impl<T> A<T> for S7<T> {}
        impl<T> C<T> for S7<T> {}

        struct S8;
        impl A<bool> for S8 {}
        impl <error descr="the trait bound `S8: A<u32>` is not satisfied [E0277]">C<u32></error> for S8 {}

        struct S9;
        impl A<u32> for S9 {}
        impl C<u32> for S9 {}
    """)

    fun `test supertrait is not implemented E0277 ignore unknown type`() = checkErrors("""
        trait A<T> {}
        trait B<T>: A<T> {}

        struct S;
        impl B<Foo> for S {}
    """)

    fun `test supertrait is not implemented E0277 self substitution`() = checkErrors("""
        trait Tr1<A=Self> {}
        trait Tr2<A=Self> : Tr1<A> {}

        struct S;
        impl Tr1 for S {}
        impl Tr2 for S {}
    """)

    fun `test supertrait is not implemented E0277 self substitution 2`() = checkErrors("""
        trait Trait<Rhs: ?Sized = Self> {}
        trait Trait2: Trait<Self> {}

        struct X<T>(T);

        impl <T> Trait for X<T> where T: Trait {}
        impl <T> Trait2 for X<T> where T: Trait<T> {}
    """)

    fun `test supertrait is not implemented E0277 self substitution 3`() = checkErrors("""
        trait Trait<Rhs: ?Sized = Self> {}
        trait Trait2: Trait<Self> {}

        struct X<T>(T);

        impl <T: Trait> Trait for X<T> {}
        impl <T> Trait2 for X<T> where T: Trait<T> {}
    """)

    fun `test supertrait is not implemented E0277 self substitution 4`() = checkErrors("""
        trait Foo {}
        trait Baz: Foo {}

        impl<T> Foo for T {}
        impl<T> Baz for T {}
    """)

    fun `test no E0277 for unknown type`() = checkErrors("""
        trait Foo {}
        trait Bar: Foo {}

        impl Bar for S {}
    """)

    fun `test no E0277 for not fully known type`() = checkErrors("""
        trait Foo {}
        trait Bar: Foo {}
        struct S<T>(T);
        impl <T: Foo> Foo for S<T> {}
        impl Bar for S<Q> {}
    """)

    fun `test no E0277 with PartialEq and Eq impls`() = checkErrors("""
        struct Box<T>(T);
        trait PartialEq<Rhs = Self> {}
        trait Eq: PartialEq<Self> {}

        impl<T: PartialEq> PartialEq for Box<T> {}
        impl<T: Eq> Eq for Box<T> {}
    """)

    // Issue // https://github.com/intellij-rust/intellij-rust/issues/8786
    fun `test no E0277 when Self-related associated type is mentioned in the parent trait`() = checkErrors("""
        struct S;
        trait Foo<T> {}
        impl Foo<S> for S {}
        trait Bar: Foo<Self::Foo> {
            type Foo;
        }
        impl Bar for S {
            type Foo = S;
        }
    """)

    fun `test parenthesized lifetime bounds`() = checkErrors("""
        fn foo<'a, T: <error descr="Parenthesized lifetime bounds are not supported">('a)</error>>(t: T) {
            unimplemented!();
        }
    """)

    fun `test crate keyword not at the beginning E0433`() = checkErrors("""
        use crate::foo::<error descr="`crate` in paths can only be used in start position [E0433]">crate</error>::Foo;
    """)

    fun `test crate keyword not at the beginning in use group E0433`() = checkErrors("""
        use crate::foo::{<error descr="`crate` in paths can only be used in start position [E0433]">crate</error>::Foo};
    """)

    @MockEdition(Edition.EDITION_2015)
    @MockRustcVersion("1.28.0")
    fun `test crate in path feature E0658`() = checkErrors("""
        mod foo {
            pub struct Foo;
        }

        use <error descr="`crate` in paths is experimental [E0658]">crate</error>::foo::Foo;
    """)

    @MockEdition(Edition.EDITION_2015)
    @MockRustcVersion("1.29.0-nightly")
    fun `test crate in path feature E0658 2`() = checkErrors("""
        mod foo {
            pub struct Foo;
        }

        use <error descr="`crate` in paths is experimental [E0658]">crate</error>::foo::Foo;
    """)

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
        trait TraitAlias = Trait;
        impl <error descr="Expected trait, found struct `S` [E0404]">S</error> for S {}
        impl <error descr="Expected trait, found enum `E` [E0404]">E</error> for S {}
        impl <error descr="Expected trait, found type alias `T` [E0404]">T</error> for S {}
        impl <error descr="Expected trait, found module `a` [E0404]">a</error> for S {}
        fn foo<A: <error descr="Expected trait, found struct `S` [E0404]">S</error>>() {}
        fn foo2<A: TraitAlias>() {}
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

    @MockRustcVersion("1.21.0-nightly")
    fun `test yield syntax feature E0658 3`() = checkErrors("""
        #![feature(generators, box_syntax)]

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

    @MockAdditionalCfgOptions("intellij_rust")
    @MockRustcVersion("1.0.0-nightly")
    fun `test box expression feature E0658 with cfg_attr with several attributes 1`() = checkErrors("""
        #![cfg_attr(intellij_rust, feature(generators), feature(box_syntax))]

        struct S;
        fn main() {
            let x = box S;
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockRustcVersion("1.0.0-nightly")
    fun `test box expression feature E0658 with cfg_attr with several attributes 2`() = checkErrors("""
        #![cfg_attr(not(intellij_rust), feature(generators), feature(box_syntax))]

        struct S;
        fn main() {
            let x = <error descr="`box` expression syntax is experimental [E0658]">box</error> S;
        }
    """)

    @MockRustcVersion("1.70.0")
    fun `test box expression feature removed`() = checkErrors("""
        struct S;
        fn main() {
            let x = <error descr="`box` expression syntax has been removed">box</error> S;
        }
    """)

    @MockRustcVersion("1.41.0")
    fun `test raw address of feature E0658 1`() = checkErrors("""
        fn main() {
            let mut x = 0;
            let _ = &<error descr="`raw address of` syntax is experimental [E0658]">raw</error> const x;
            let _ = &<error descr="`raw address of` syntax is experimental [E0658]">raw</error> mut x;
        }
    """)

    @MockRustcVersion("1.41.0-nightly")
    fun `test raw address of feature E0658 2`() = checkErrors("""
        #![feature(raw_ref_op)]

        fn main() {
            let mut x = 0;
            let _ = &raw const x;
            let _ = &raw mut x;
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
            if let <error descr="irrefutable let pattern is experimental [E0658]">ST(x)</error> = b {}
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
    fun `test irrefutable let else`() = checkErrors("""
        fn main() {
            let x = Some(0);
            let <error descr="irrefutable let pattern is experimental [E0658]">x</error> = Some(0) <error descr="let else is experimental [E0658]">else { return }</error>;
            let Some(x) = Some(0) <error descr="let else is experimental [E0658]">else { return }</error>;
        }
    """)

    @MockRustcVersion("1.56.0")
    fun `test let else E0658 1`() = checkErrors("""
        fn main() {
            let Some(x) = Some(1) <error descr="let else is experimental [E0658]">else { return; }</error>;
        }
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test let else E0658 2`() = checkErrors("""
        #![feature(let_else)]
        fn main() {
            let Some(x) = Some(1) else { return; };
        }
    """)

    @MockRustcVersion("1.56.0")
    fun `test let chains E0658 1`() = checkErrors("""
        fn foo(x: Option<i32>) {
            if let Some(_) = x {};
            if (<error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error>) {};
            if <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> && <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> {};
            if (<error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error>) && (<error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error>) {};
            if (<error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> && <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error>) {};
            if <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> || <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> {};

            match 0 {
                _ if <error descr="if let guard is experimental [E0658]">let</error> Some(_) = x => 1,
                _ if (<error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error>) => 2,
                _ if <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> && <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> => 3,
                _ if (<error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error>) && (<error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error>) => 4,
                _ if (<error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> && <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error>) => 5,
                _ if <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> || <error descr="`let` expressions in this position are unstable [E0658]">let Some(_) = x</error> => 6,
                _ => 7
            };
        }
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test let chains E0658 2`() = checkErrors("""
        #![feature(let_chains)]
        #![feature(if_let_guard)]
        fn foo(x: Option<i32>) {
            if let Some(_) = x {};
            if (let Some(_) = x) {};
            if let Some(_) = x && let Some(_) = x {};
            if (let Some(_) = x) && (let Some(_) = x) {};
            if (let Some(_) = x && let Some(_) = x) {};
            if let Some(_) = x || let Some(_) = x {};

            match 0 {
                _ if let Some(_) = x => 1,
                _ if (let Some(_) = x) => 2,
                _ if let Some(_) = x && let Some(_) = x => 3,
                _ if (let Some(_) = x) && (let Some(_) = x) => 4,
                _ if (let Some(_) = x && let Some(_) = x) => 5,
                _ if let Some(_) = x || let Some(_) = x => 6,
                _ => 7
            };
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

    @MockRustcVersion("1.38.0-nightly")
    fun `test top level or patterns E0658 1`() = checkErrors("""
        enum V { V1(i32), V2(i32) }
        fn foo(y: V) {
            let <error descr="or-patterns syntax is experimental [E0658]">V::V1(x) | V::V2(x)</error> = y;
            if let V::V1(x) | V::V2(x) = y {}
            while let V::V1(x) | V::V2(x) = y {}
            match y {
                V::V1(x) | V::V2(x) => {},
                _ => {}
            }
        }
    """)

    @MockRustcVersion("1.38.0-nightly")
    fun `test or patterns in let decl E0658 2`() = checkErrors("""
        #![feature(or_patterns)]
        enum V { V1(i32), V2(i32) }
        fn foo(y: V) {
            let V::V1(x) | V::V2(x) = y;
            if let V::V1(x) | V::V2(x) = y {}
            while let V::V1(x) | V::V2(x) = y {}
            match y {
                V::V1(x) | V::V2(x) => {},
                _ => {}
            }
        }
    """)

    @MockRustcVersion("1.38.0")
    fun `test non top level or patterns E0658 1`() = checkErrors("""
        enum Option<T> { None, Some(T) }
        enum V { V1(i32), V2(i32) }
        fn foo(y: Option<V>) {
            if let Option::Some(<error descr="or-patterns syntax is experimental [E0658]">V::V1(x) | V::V2(x)</error>) = y {}
            while let Option::Some(<error descr="or-patterns syntax is experimental [E0658]">V::V1(x) | V::V2(x)</error>) = y {}
            match y {
                Option::Some(<error descr="or-patterns syntax is experimental [E0658]">V::V1(x) | V::V2(x)</error>) => {},
                _ => {}
            }
        }
    """)

    @MockRustcVersion("1.38.0-nightly")
    fun `test non top level or patterns E0658 2`() = checkErrors("""
        #![feature(or_patterns)]
        enum Option<T> { None, Some(T) }
        enum V { V1(i32), V2(i32) }
        fn foo(y: V) {
            if let Option::Some(V::V1(x) | V::V2(x)) = y {}
            while let Option::Some(V::V1(x) | V::V2(x)) = y {}
            match y {
                Option::Some(V::V1(x) | V::V2(x)) => {},
                _ => {}
            }
        }
    """)

    @MockRustcVersion("1.53.0")
    fun `test non top level or patterns E0658 3`() = checkErrors("""
        enum Option<T> { None, Some(T) }
        enum V { V1(i32), V2(i32) }
        fn foo(y: V) {
            if let Option::Some(V::V1(x) | V::V2(x)) = y {}
            while let Option::Some(V::V1(x) | V::V2(x)) = y {}
            match y {
                Option::Some(V::V1(x) | V::V2(x)) => {},
                _ => {}
            }
        }
    """)

    @MockRustcVersion("1.38.0-nightly")
    fun `test leading vertical bar in toplevel or patterns`() = checkErrors("""
        #![feature(or_patterns)]
        enum V { V1(i32), V2(i32) }
        fn foo(y: V, z: V) {
            if let | V::V1(x) | V::V2(x) = y {}
            match z {
                | V::V1(x) | V::V2(x) => {},
            }
        }
    """)

    @MockRustcVersion("1.53.0")
    fun `test leading vertical bar in nested or patterns`() = checkErrors("""
        enum Option<T> { None, Some(T) }
        enum V { V1(i32), V2(i32) }
        fn foo(y: Option<V>) {
            while let Option::Some(|/*caret*/ V::V1(x) | V::V2(x)) = &y {}
            if let (| Option::None | Option::Some(_), _) = (&y, &y) {}
            match &[y] {
                [.., | Option::None | Option::Some(_)] => {},
                _ => {}
            }
        }
    """)

    @SkipTestWrapping // Attribute macro on extern crate declaration is not supported for now
    @MockRustcVersion("1.33.0")
    fun `test extern_crate_self 1`() = checkErrors("""
        <error descr="`extern crate self` is experimental [E0658]">extern crate self as foo;</error>
    """)

    @MockRustcVersion("1.33.0-nightly")
    fun `test extern_crate_self 2`() = checkErrors("""
        #![feature(extern_crate_self)]

        extern crate self as foo;
    """)

    @SkipTestWrapping // Attribute macro on extern crate declaration is not supported for now
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

    fun `test expected function on a impl NotFn E0618`() = checkErrors("""
        struct Foo;
        trait NotFn<Args> {
            type Output;
            fn call(&self, args: Args) -> Self::Output;
        }
        impl NotFn<()> for Foo {
            type Output = ();
            fn call(&self, (): ()) {}
        }

        fn bar() {
            <error descr="Expected function, found `Foo` [E0618]">Foo</error>();
        }
    """)

    @MockRustcVersion("1.68.0")
    fun `test no 'expected function' on a manual FnOnce impl E0618`() = checkErrors("""
        struct Foo;
        #[lang = "fn_once"]
        trait FnOnce<Args> {
            type Output;
            fn call_once(self, args: Args) -> Self::Output;
        }
        impl /*error descr="Manual implementations of `FnOnce` are experimental [E0183]"*//*error descr="The precise format of `Fn`-family traits' type parameters is subject to change [E0658]"*/FnOnce<()>/*error**//*error**/ for Foo {
            type Output = ();
            fn call_once(self, (): ()) {}
        }

        fn bar() {
            Foo(); // No E0618 here
        }
    """)

    @MockRustcVersion("1.34.0")
    fun `test label_break_value 1`() = checkErrors("""
        fn main() {
            <error descr="label on block is experimental [E0658]">'a:</error> {
                if true { break 'a 1; }
                2
            }
        }
    """)

    @MockRustcVersion("1.34.0-nightly")
    fun `test label_break_value 2`() = checkErrors("""
        #![feature(label_break_value)]

        fn main() {
            'a: {
                if true { break 'a 1; }
                2
            }
        }
    """)

    @MockRustcVersion("1.34.0-nightly")
    fun `test break without label in labeled block E0695`() = checkErrors("""
        #![feature(label_break_value)]

        fn main() {
            'a: {
                if true { <error descr="Unlabeled `break` inside of a labeled block [E0695]">break</error> 1; }
                2
            }
        }
    """)

    @MockRustcVersion("1.34.0-nightly")
    fun `test continue without label in labeled block E0695`() = checkErrors("""
        #![feature(label_break_value)]

        fn main() {
            'a: {
                if true { <error descr="Unlabeled `continue` inside of a labeled block [E0695]">continue</error>; }
                2
            }
        }
    """)

    fun `test duplicate enum discriminant #1 E0081`() = checkErrors("""
        enum Bad {
            <error descr="Discriminant value `0` already exists [E0081]">X</error>,
            Y = <error descr="Discriminant value `0` already exists [E0081]">0</error>,
        }
    """)

    fun `test duplicate enum discriminant #2 E0081`() = checkErrors("""
        enum Bad {
            P = <error descr="Discriminant value `3` already exists [E0081]">3</error>,
            Y = 0,
            X = <error descr="Discriminant value `3` already exists [E0081]">3</error>,
        }
    """)

    fun `test duplicate enum discriminant #3 E0081`() = checkErrors("""
        enum Bad {
            X = <error descr="Discriminant value `0` already exists [E0081]">0</error>,
            Y = <error descr="Discriminant value `0` already exists [E0081]">0</error>,
            Z = <error descr="Discriminant value `0` already exists [E0081]">0</error>,
            W = <error descr="Discriminant value `0` already exists [E0081]">0</error>,
        }
    """)


    fun `test duplicate enum discriminant #4 E0081`() = checkErrors("""
        enum Good {
            X = 0,
            Y = 1,
            Z = 2,
            W = 3,
        }
    """)

    fun `test duplicate enum discriminant #5 E0081`() = checkErrors("""
        enum Good {
            X = 1,
            Y,
        }
    """)

    fun `test duplicate enum discriminant #6 E0081`() = checkErrors("""
        enum Good {
            X,
            Y = 1,
            Z
        }
    """)

    fun `test duplicate enum discriminant #7 E0081`() = checkErrors("""
        enum Good {
            X,
            Y = 2,
            Z
        }
    """)

    fun `test duplicate enum discriminant const expr E0081`() = checkErrors("""
                enum Bad {
            X = <error descr="Discriminant value `3` already exists [E0081]">1 + 5 - 3</error>,
            Y = <error descr="Discriminant value `3` already exists [E0081]">1 + 2</error>
        }
    """)

    fun `test duplicate enum discriminant non-const expr E0081`() = checkErrors("""
        fn foo() -> isize { 0 }
        enum Good {
            X = foo(),
            Y = 0
        }
    """)

    fun `test duplicate enum discriminant repr type valid range E0081`() = checkErrors("""
        #[repr(i8)]
        enum Bad {
            X = <error descr="Discriminant value `-1` already exists [E0081]">-1</error>,
            Y = <error descr="Discriminant value `-1` already exists [E0081]">-1</error>
        }
    """)

    fun `test duplicate enum discriminant repr type invalid range E0081`() = checkErrors("""
        #[repr(u8)]
        enum Good {
            X = -1,
            Y = -1
        }
    """)


    fun `test E0040`() = checkErrors("""
        struct X;
        #[lang = "drop"]
        pub trait Drop {
            fn drop(&mut self);
        }
        impl Drop for X {
            fn drop(&mut self) {}
        }
        struct XFactory;
        impl XFactory {
            fn create_x(&self, foo: u32) -> X {
                X {}
            }
        }

        fn main() {
            let mut x = X {};
            <error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">Drop::drop</error>(&mut x);
            <error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">X::drop</error>(&mut x);
            x.<error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">drop</error>();
            XFactory {}.create_x(123).<error descr="Explicit calls to `drop` are forbidden. Use `std::mem::drop` instead [E0040]">drop</error>();
        }
    """)

    fun `test E0040 fake drop`() = checkErrors("""
        struct X;
        impl X {
            // Note this has nothing to do with the actual core::ops::drop::Drop and is just a regular function
            fn drop(&mut self) {}
        }
        struct XFactory;
        impl XFactory {
            fn create_x(&self, foo: u32) -> X {
                X {}
            }
        }


        fn main() {
            let mut x = X {};
            X::drop(&mut x);
            x.drop();
            XFactory {}.create_x(123).drop();
        }
    """)

    fun `test impl Drop for Trait E0120`() = checkErrors("""
        trait Trait {}
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self);
        }
        impl <error descr="Drop can be only implemented by structs and enums [E0120]">Drop</error> for Trait {
            fn drop(&mut self) {}
        }
    """)

    fun `test impl Drop for primitive E0120`() = checkByText("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self);
        }
        impl <error descr="Drop can be only implemented by structs and enums [E0120]">Drop</error> for u32 {
            fn drop(&mut self) {}
        }
    """, ignoreExtraHighlighting = true /* impl X for u32 is also an error so we ignore it here */)

    fun `test impl Drop for reference E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self);
        }
        impl<'a> <error descr="Drop can be only implemented by structs and enums [E0120]">Drop</error> for &'a str {
            fn drop(&mut self) {}
        }
    """)

    fun `test impl Drop for array E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self);
        }
        impl <error descr="Drop can be only implemented by structs and enums [E0120]">Drop</error> for [u32; 1] {
            fn drop(&mut self) {}
        }
    """)

    fun `test impl Drop for pointer E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self);
        }
        impl <error descr="Drop can be only implemented by structs and enums [E0120]">Drop</error> for *u32 {
            fn drop(&mut self) {}
        }
    """)

    fun `test impl for blank E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self) {}
        }
        // E0120 should not be triggered
        impl Drop for<error descr="'..' or <type> expected, got '{'"> </error>{}
    """)

    fun `test impl for struct E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self) {}
        }
        struct Foo; // E0120 should not get triggered
        impl Drop for Foo {}
    """)

    fun `test impl for enum E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self) {}
        }
        enum Foo {} // E0120 should not get triggered
        impl Drop for Foo {}
    """)

    fun `test impl for union E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self) {}
        }
        union Foo { x: u32 } // E0120 should not get triggered
        impl Drop for Foo {}
    """)

    fun `test impl FakeDrop for Trait E0120`() = checkErrors("""
        trait Drop {}
        trait Trait {}
        impl Drop for Trait {}
    """)

    fun `test impl for normalizable associated type referring to a struct E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self) {}
        }
        struct S;
        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = S; }
        impl Drop for <Struct as Trait>::Item {}
    """)

    fun `test impl for normalizable associated type referring to a primitive E0120`() = checkErrors("""
        #[lang = "drop"]
        trait Drop {
            fn drop(&mut self) {}
        }
        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = u8; }
        impl <error descr="Drop can be only implemented by structs and enums [E0120]">Drop</error> for <Struct as Trait>::Item {}
    """)

    fun `test impl Drop and derive Copy E0184`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}
        #[lang = "drop"]
        trait Drop {}

        #[derive(<error descr="Cannot implement both Copy and Drop [E0184]">Copy</error>)]
        struct Foo;

        impl <error descr="Cannot implement both Copy and Drop [E0184]">Drop</error> for Foo {}
    """)

    fun `test impl Drop and impl Copy E0184`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}
        #[lang = "drop"]
        trait Drop {}

        struct Foo;

        impl <error descr="Cannot implement both Copy and Drop [E0184]">Copy</error> for Foo {}
        impl <error descr="Cannot implement both Copy and Drop [E0184]">Drop</error> for Foo {}
    """)

    fun `test impl Drop and impl Copy on generic struct E0184`() = checkErrors("""
        #[lang = "copy"]
        trait Copy {}
        #[lang = "drop"]
        trait Drop {}
        struct Foo<T>(T);

        impl<T> <error descr="Cannot implement both Copy and Drop [E0184]">Copy</error> for Foo<T> {}
        impl<T> <error descr="Cannot implement both Copy and Drop [E0184]">Drop</error> for Foo<T> {}
    """)

    // TODO fix impl search for associated types
    fun `test impl Drop and impl Copy for normalizable associated type E0184 1`() = expect<org.junit.ComparisonFailure> {
    checkErrors("""
        #[lang = "copy"] trait Copy {}
        #[lang = "drop"] trait Drop {}

        struct Foo;

        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = Foo; }

        impl <error descr="Cannot implement both Copy and Drop [E0184]">Copy</error> for <Struct as Trait>::Item {}
        impl <error descr="Cannot implement both Copy and Drop [E0184]">Drop</error> for <Struct as Trait>::Item {}
    """)
    }

    fun `test impl Drop and impl Copy for normalizable associated type E0184 2`() = checkErrors("""
        #[lang = "copy"] trait Copy {}
        #[lang = "drop"] trait Drop {}

        struct Foo;

        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = Foo; }

        impl Copy for Foo {} // TODO should be an error too
        impl <error descr="Cannot implement both Copy and Drop [E0184]">Drop</error> for <Struct as Trait>::Item {}
    """)

    fun `test outer inline attr on function E0518`() = checkErrors("""
        #[inline]
        fn foo() {}
        #[inline(always)]
        fn bar() {}
    """)

    fun `test inner inline attr on function E0518`() = checkErrors("""
        fn foo() { #![inline] }
        fn bar() { #![inline(always)] }
    """)

    fun `test outer inline attr E0518`() = checkErrors("""
        #[<error descr="Attribute should be applied to function or closure [E0518]">inline</error>]
        struct Foo;
        #[<error descr="Attribute should be applied to function or closure [E0518]">inline</error>(always)]
        enum Bar {}
    """)

    fun `test inner inline attr E0518`() = checkErrors("""
        fn main() {
            #[<error descr="Attribute should be applied to function or closure [E0518]">inline</error>]
            let x = "foo";
            #[<error descr="Attribute should be applied to function or closure [E0518]">inline</error>(always)]
            let y = "bar";
        }
    """)

    fun `test impl unknown E0118`() = checkErrors("""
        impl Foo {} // Should not report errors for unresolved types
    """)

    fun `test impl u8 E0118`() = checkErrors("""
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">u8</error> {}
    """)

    fun `test impl tuple E0118`() = checkErrors("""
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">(u8, u8)</error> {}
    """)

    fun `test impl array E0118`() = checkErrors("""
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">[u8; 1]</error> {}
    """)

    fun `test impl pointer E0118`() = checkErrors("""
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">*const u8</error> {}
    """)

    fun `test impl dyn Trait E0118`() = checkErrors("""
        trait Bar {}
        impl dyn Bar {
            fn foo(&self) {}
        }
    """)

    fun `test impl const ptr E0118`() = checkErrors("""
        impl<T> <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">*const T</error> {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test impl feature lang const ptr E0118`() = checkErrors("""
        #![feature(lang_items)]
        #[lang = "const_ptr"]
        impl<T> *const T {}
    """)

    fun `test impl mut ptr E0118`() = checkErrors("""
        impl<T> <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">*mut T</error> {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test impl feature lang mut ptr E0118`() = checkErrors("""
        #![feature(lang_items)]
        #[lang = "mut_ptr"]
        impl<T> *mut T {}
    """)

    fun `test impl slice E0118`() = checkErrors("""
        impl<T> <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">[T]</error> {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test impl feature lang slice E0118`() = checkErrors("""
        #![feature(lang_items)]
        #[lang = "slice"]
        impl<T> [T] {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test impl feature lang slice alloc E0118`() = checkErrors("""
        #![feature(lang_items)]
        #[lang = "slice_alloc"]
        impl<T> [T] {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test impl feature lang slice u8 E0118`() = checkErrors("""
        #![feature(lang_items)]
        #[lang = "slice_u8"]
        impl [u8] {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test impl feature lang slice u8 alloc E0118`() = checkErrors("""
        #![feature(lang_items)]
        #[lang = "slice_u8_alloc"]
        impl [u8] {}
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test impl feature lang u8 E0118`() = checkErrors("""
        #![feature(lang_items)]
        #[lang = "u8"]
        impl u8 {}
    """)

    fun `test no core impl u8 E0118`() = checkErrors("""
        #![no_core]
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">u8</error> {}
    """)

    fun `test feature no core impl u8 E0118`() = checkErrors("""
        #![feature(no_core)]
        #![no_core]
        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]">u8</error> {}
    """)

    fun `test E0118 impl for normalizable associated type 1`() = checkErrors("""
        struct S;
        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = S; }

        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]"><Struct as Trait>::Item</error> {}
    """)

    fun `test E0118 impl for normalizable associated type 2`() = checkErrors("""
        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = u8; }

        impl <error descr="Can impl only `struct`s, `enum`s, `union`s and trait objects [E0118]"><Struct as Trait>::Item</error> {}
    """)

    fun `test impl sized for struct E0322`() = checkErrors("""
        #[lang = "sized"]
        trait Sized {}

        struct Foo;
        impl <error descr="Explicit impls for the `Sized` trait are not permitted [E0322]">Sized</error> for Foo {}
    """)

    fun `test impl sized for enum E0322`() = checkErrors("""
        #[lang = "sized"]
        trait Sized {}

        enum Foo {}
        impl <error descr="Explicit impls for the `Sized` trait are not permitted [E0322]">Sized</error> for Foo {}
    """)

    fun `test impl sized for trait E0322`() = checkErrors("""
        #[lang = "sized"]
        trait Sized {}

        trait Foo {}
        impl <error descr="Explicit impls for the `Sized` trait are not permitted [E0322]">Sized</error> for Foo {}
    """)

    fun `test impl unsize for struct E0328`() = checkErrors("""
        #[lang = "unsize"]
        trait Unsize {}

        struct Foo;
        impl <error descr="Explicit impls for the `Unsize` trait are not permitted [E0328]">Unsize</error> for Foo {}
    """)

    fun `test impl unsize for enum E0328`() = checkErrors("""
        #[lang = "unsize"]
        trait Unsize {}

        enum Foo {}
        impl <error descr="Explicit impls for the `Unsize` trait are not permitted [E0328]">Unsize</error> for Foo {}
    """)

    fun `test impl unsize for trait E0328`() = checkErrors("""
        #[lang = "unsize"]
        trait Unsize {}

        trait Foo {}
        impl <error descr="Explicit impls for the `Unsize` trait are not permitted [E0328]">Unsize</error> for Foo {}
    """)

    fun `test nested impl Trait not allowed E0666`() = checkErrors("""
        impl Trait<T>{}

        fn nested_in_return_bad() -> impl Trait<<error descr="nested `impl Trait` is not allowed [E0666]">impl Debug</error>> { panic!() }

        fn nested_in_argument_bad(arg: impl Trait<<error descr="nested `impl Trait` is not allowed [E0666]">impl Debug</error>>) {panic!()}

        fn allowed_in_assoc_type() -> impl Iterator<Item=impl Fn()> {
            vec![|| println!("woot")].into_iter()
        }
    """)

    fun `test impl Trait not allowed in path params E667`() = checkErrors("""
        use std::fmt::Debug;
        use std::option;

        fn parametrized_type_is_allowed() -> Option<impl Debug> {
            Some(5i32)
        }

        fn path_parametrized_type_is_allowed() -> option::Option<impl Debug> {
            Some(5i32)
        }

        fn projection_is_disallowed(x: impl Iterator) -> <<error descr="`impl Trait` is not allowed in path parameters [E0667]">impl Iterator</error>>::Item {
        //~^ ERROR `impl Trait` is not allowed in path parameters
            x.next().unwrap()
        }

        fn projection_with_named_trait_is_disallowed(x: impl Iterator)
            -> <<error descr="`impl Trait` is not allowed in path parameters [E0667]">impl Iterator</error> as Iterator>::Item
        //~^ ERROR `impl Trait` is not allowed in path parameters
        {
            x.next().unwrap()
        }

        fn projection_with_named_trait_inside_path_is_disallowed()
            -> <::std::ops::Range<<error descr="`impl Trait` is not allowed in path parameters [E0667]">impl Debug</error>> as Iterator>::Item
        //~^ ERROR `impl Trait` is not allowed in path parameters
        {
            (1i32..100).next().unwrap()
        }

        fn projection_from_impl_trait_inside_dyn_trait_is_disallowed()
            -> <dyn Iterator<Item = <error descr="`impl Trait` is not allowed in path parameters [E0667]">impl Debug</error>> as Iterator>::Item
        //~^ ERROR `impl Trait` is not allowed in path parameters
        {
            panic!()
        }

        fn main() {}
            """)

    fun `test impl Trait not allowed E0562`() = checkErrors("""
        trait Debug{}
        // Allowed
        fn in_parameters(_: impl Debug) { panic!() }

        // Allowed
        fn in_return() -> impl Debug { panic!() }

        // Allowed
        fn in_adt_in_parameters(_: Vec<impl Debug>) { panic!() }

        // Allowed
        fn in_adt_in_return() -> Vec<impl Debug> { panic!() }

        fn in_fn_parameter_in_parameters(_: fn(<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>)) { panic!() }

        fn in_fn_return_in_parameters(_: fn() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>) { panic!() }

        fn in_fn_parameter_in_return() -> fn(<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>) { panic!() }

        fn in_fn_return_in_return() -> fn() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error> { panic!() }

        fn in_dyn_Fn_parameter_in_parameters(_: &dyn Fn(<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>)) { panic!() }

        fn in_dyn_Fn_return_in_parameters(_: &dyn Fn() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>) { panic!() }

        fn in_dyn_Fn_parameter_in_return() -> &'static dyn Fn(<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>) { panic!() }

        fn in_dyn_Fn_return_in_return() -> &'static dyn Fn() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error> { panic!() }

        fn in_impl_Fn_return_in_parameters(_: &impl Fn() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]"><error descr="nested `impl Trait` is not allowed [E0666]">impl Debug</error></error>) { panic!() }

        fn in_impl_Fn_return_in_return() -> &'static impl Fn() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]"><error descr="nested `impl Trait` is not allowed [E0666]">impl Debug</error></error> { panic!() }

        fn in_Fn_parameter_in_generics<F: Fn(<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>)> (_: F) { panic!() }

        fn in_Fn_return_in_generics<F: Fn() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>> (_: F) { panic!() }

        // Allowed
        fn in_impl_Trait_in_parameters(_: impl Iterator<Item = impl Iterator>) { panic!() }

        // Allowed
        fn in_impl_Trait_in_return() -> impl IntoIterator<Item = impl IntoIterator> {
            vec![vec![0; 10], vec![12; 7], vec![8; 3]]
        }

        struct InBraceStructField { x: <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error> }

        struct InAdtInBraceStructField { x: Vec<<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>> }

        struct InTupleStructField(<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>);

        enum InEnum {
            InBraceVariant { x: <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error> },
            InTupleVariant(<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>),
        }

        // Allowed
        trait InTraitDefnParameters {
            fn in_parameters(_: impl Debug);
        }

        // Allowed and disallowed in trait impls
        trait DummyTrait {
//            type Out;
            fn in_trait_impl_parameter(_: impl Debug);
            fn wrapper();
        }
        impl DummyTrait for () {
//            type Out = impl Debug;
            //~^ ERROR `impl Trait` not allowed outside of function and inherent method return types

            fn in_trait_impl_parameter(_: impl Debug) { }
            // Allowed

            fn wrapper() {
                fn in_nested_impl_return() -> impl Debug { () }
                // Allowed
            }
        }

        // Allowed
        struct DummyType;
        impl DummyType {
            fn in_inherent_impl_parameters(_: impl Debug) { }
            fn in_inherent_impl_return() -> impl Debug { () }
        }

        // Disallowed
        extern "C" {
            fn in_foreign_parameters(_: <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>);

            fn in_foreign_return() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>;
        }

        // Allowed
        extern "C" fn in_extern_fn_parameters(_: impl Debug) {
        }

        // Allowed
        extern "C" fn in_extern_fn_return() -> impl Debug {
            22
        }

//        type InTypeAlias<R> = impl Debug;
        //~^ ERROR `impl Trait` not allowed outside of function and inherent method return types

//        type InReturnInTypeAlias<R> = fn() -> impl Debug;
        //~^ ERROR `impl Trait` not allowed outside of function and inherent method return types

        impl PartialEq<<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>> for () {
        }

        impl PartialEq<()> for <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error> {
        }

        impl <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error> {
        }

        struct InInherentImplAdt<T> { t: T }
        impl InInherentImplAdt<<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>> {
        }

        fn in_fn_where_clause()
            where <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>: Debug{
        }

        fn in_adt_in_fn_where_clause()
            where Vec<<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>>: Debug{
        }

        fn in_trait_parameter_in_fn_where_clause<T>()
            where T: PartialEq<<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>>{
        }

        fn in_Fn_parameter_in_fn_where_clause<T>()
            where T: Fn(<error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error>){
        }

        fn in_Fn_return_in_fn_where_clause<T>()
            where T: Fn() -> <error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]">impl Debug</error> {
        }

        trait TraitAssoc {
            type Item;
        }

        fn in_associated_type<T: TraitAssoc<Item=impl Debug>>() {}

        fn main() {
//            let _in_local_variable: impl Fn() = || {};
            //~^ ERROR `impl Trait` not allowed outside of function and inherent method return types
            let _in_return_in_local_variable = || -> impl Fn() { || {} };
        }
    """)

    @MockRustcVersion("1.69.0-nightly")
    fun `test feature gated impl Trait not allowed E0562`() = checkErrors("""
        #![feature(return_position_impl_trait_in_trait)]
        trait Debug {}

        trait InTraitDefnReturn {
            fn in_return() -> impl Debug;
        }

        trait DummyTrait {
            fn in_trait_impl_parameter(_: impl Debug);
            fn in_trait_impl_return() -> Self::Out;
            fn wrapper();
        }

        impl DummyTrait for () {
            fn in_trait_impl_parameter(_: impl Debug) { }
            fn in_trait_impl_return() -> impl Debug { () }
            fn wrapper() {
                fn in_nested_impl_return() -> impl Debug { () }
            }
        }

        trait Nested {
            fn deref(&self) -> impl Deref<Target = impl Display> + '_;
        }
    """)

    @MockRustcVersion("1.69.0-nightly")
    fun `test feature gated impl Trait not allowed E0562 fix`() = checkFixByText("Add `return_position_impl_trait_in_trait` feature", """
        trait Debug {}

        trait InTraitDefnReturn {
            fn in_return() -> /*error descr="`impl Trait` not allowed outside of function and inherent method return types [E0562]"*/impl/*caret*/ Debug/*error**/;
        }
    """, """
        #![feature(return_position_impl_trait_in_trait)]

        trait Debug {}

        trait InTraitDefnReturn {
            fn in_return() -> impl/*caret*/ Debug;
        }
    """, preview = null)

    @MockRustcVersion("1.42.0")
    fun `test const trait impl E0658 1`() = checkErrors("""
        struct S;
        trait T {}
        impl <error descr="const trait impls is experimental [E0658]">const</error> S {}
        impl <error descr="const trait impls is experimental [E0658]">const</error> T for S {}
    """)

    @MockRustcVersion("1.42.0-nightly")
    fun `test const trait impl E0658 2`() = checkErrors("""
        #![feature(const_trait_impl)]
        struct S;
        trait T {}
        impl const S {} // TODO: inherent impls cannot be `const`
        impl const T for S {}
    """)

    @MockRustcVersion("1.53.0")
    fun `test const fn trait bound E0658 1`() = checkErrors("""
        trait T {}
        const fn foo<A: <error descr="const trait impls is experimental [E0658]"><error descr="const fn trait bound is experimental [E0658]">~const</error></error> T>() {}
    """)

    @MockRustcVersion("1.53.0-nightly")
    fun `test const fn trait bound E0658 2`() = checkErrors("""
        #![feature(const_trait_impl)]
        trait T {}
        const fn foo<A: <error descr="const fn trait bound is experimental [E0658]">~const</error> T>() {}
    """)

    @MockRustcVersion("1.53.0-nightly")
    fun `test const fn trait bound E0658 3`() = checkErrors("""
        #![feature(const_fn_trait_bound)]
        trait T {}
        const fn foo<A: <error descr="const trait impls is experimental [E0658]">~const</error> T>() {}
    """)

    @MockRustcVersion("1.53.0-nightly")
    fun `test const fn trait bound E0658 4`() = checkErrors("""
        #![feature(const_trait_impl)]
        #![feature(const_fn_trait_bound)]
        trait T {}
        const fn foo<A: ~const T>() {}
    """)

    @MockRustcVersion("1.56.0")
    fun `test const generics`() = checkErrors("""
        fn f<const C: i32>() {}
        struct S<const C: i32>(A);
        trait T<const C: i32> {}
        enum E<const C: i32> {}
    """)


    @MockRustcVersion("1.50.0")
    fun `test min const generics E0658 1`() = checkErrors("""
        fn f<<error descr="min const generics is experimental [E0658]">const C: i32</error>>() {}
        struct S<<error descr="min const generics is experimental [E0658]">const C: i32</error>>(A);
        trait T<<error descr="min const generics is experimental [E0658]">const C: i32</error>> {}
        enum E<<error descr="min const generics is experimental [E0658]">const C: i32</error>> {}
    """)

    @MockRustcVersion("1.51.0-nightly")
    fun `test min const generics E0658 2`() = checkErrors("""
        #![feature(min_const_generics)]
        fn f<const C: i32>() {}
        struct S<const C: i32>(A);
        trait T<const C: i32> {}
        enum E<const C: i32> {}
    """)

    @MockRustcVersion("1.56.0")
    fun `test adt const params E0658 1`() = checkErrors("""
        struct F;
        fn f<const C: <error descr="adt const params is experimental [E0658]">F</error>>() {}
        struct S<const C: <error descr="adt const params is experimental [E0658]">F</error>>(A);
        trait T<const C: <error descr="adt const params is experimental [E0658]">F</error>> {}
        enum E<const C: <error descr="adt const params is experimental [E0658]">F</error>> {}
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test adt const params E0658 2`() = checkErrors("""
        #![feature(adt_const_params)]
        struct F;
        fn f<const C: F>() {}
        struct S<const C: F>(A);
        trait T<const C: F> {}
        enum E<const C: F> {}
    """)

    @MockRustcVersion("1.51.0")
    fun `test const generics defaults E0658 1`() = checkErrors("""
        fn f<const C: i32 = <error descr="Defaults for const parameters are only allowed in `struct`, `enum`, `type`, or `trait` definitions"><error descr="const generics defaults is experimental [E0658]">0</error></error>>() {}
        struct S<const C: i32 = <error descr="const generics defaults is experimental [E0658]">0</error>>(A);
        trait T<const C: i32 = <error descr="const generics defaults is experimental [E0658]">0</error>> {}
        impl <const C: i32 = <error descr="Defaults for const parameters are only allowed in `struct`, `enum`, `type`, or `trait` definitions"><error descr="const generics defaults is experimental [E0658]">0</error></error>> T<C> for S<C> {}
        enum E<const C: i32 = <error descr="const generics defaults is experimental [E0658]">0</error>> {}
        type A<const C: i32 = <error descr="const generics defaults is experimental [E0658]">0</error>> = S<C>;
    """)

    @MockRustcVersion("1.51.0-nightly")
    fun `test const generics defaults E0658 2`() = checkErrors("""
        #![feature(const_generics_defaults)]
        fn f<const C: i32 = <error descr="Defaults for const parameters are only allowed in `struct`, `enum`, `type`, or `trait` definitions">0</error>>() {}
        struct S<const C: i32 = 0>(A);
        trait T<const C: i32 = 0> {}
        impl <const C: i32 = <error descr="Defaults for const parameters are only allowed in `struct`, `enum`, `type`, or `trait` definitions">0</error>> T<C> for S<C> {}
        enum E<const C: i32 = 0> {}
        type A<const C: i32 = 0> = S<C>;
    """)

    @MockRustcVersion("1.41.0")
    fun `test slice patterns E0658 1`() = checkErrors("""
        fn main() {
            let [x, <error descr="subslice patterns is experimental [E0658]">..</error>] = [1, 2];
            let [x, xs @ <error descr="subslice patterns is experimental [E0658]">..</error>] = [1, 2];
            let [x, xs @ <error descr="subslice patterns is experimental [E0658]">..</error>] = &[1, 2];

            let (x, ..) = (1, 2);
            let (x, xs @ ..) = (1, 2); // TODO: `..` patterns are not allowed here

            let [(x, ..)] = [(1, 2)];
            let [(x, xs @ ..)] = [(1, 2)]; // TODO: `..` patterns are not allowed here
        }
    """)

    @MockRustcVersion("1.41.0-nightly")
    fun `test slice patterns E0658 2`() = checkErrors("""
        #![feature(slice_patterns)]
        fn main() {
            let [x, ..] = [1, 2];
            let [x, xs @ ..] = [1, 2];
            let [x, xs @ ..] = &[1, 2];
        }
    """)

    @MockRustcVersion("1.47.0")
    fun `test if let guard E0658 1`() = checkErrors("""
        fn main() {
            let xs = vec![0i32];
            match xs.len() {
                1 if <error descr="if let guard is experimental [E0658]">let</error> Some(x) = xs.iter().next() => {}
                _ => unreachable!(),
            }
        }
    """)

    @MockRustcVersion("1.47.0-nightly")
    fun `test if let guard E0658 2`() = checkErrors("""
        #![feature(if_let_guard)]
        fn main() {
            let xs = vec![0i32];
            match xs.len() {
                1 if let Some(x) = xs.iter().next() => {}
                _ => unreachable!(),
            }
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test stable attr on invalid owner E0132`() = checkErrors("""
        #![feature(start)]
        #[<error descr="Start attribute can be placed only on functions [E0132]">start</error>]
        struct Foo;
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test stable attr on fn with return mismatch E0132`() = checkErrors("""
        #![feature(start)]
        #[start]
        fn test_name(_argc: isize, _argv: *const *const u8) -> <error descr="Functions with a `start` attribute must return `isize` [E0132]">u32</error> { 0 }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test stable attr on fn without return E0132`() = checkErrors("""
        #![feature(start)]
        #[start]
        fn <error descr="Functions with a `start` attribute must return `isize` [E0132]">test_name</error>(_argc: isize, _argv: *const *const u8) { 0 }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test inner stable attr on fn with return mismatch E0132`() = checkErrors("""
        #![feature(start)]
        fn test_name(_argc: isize, _argv: *const *const u8) -> <error descr="Functions with a `start` attribute must return `isize` [E0132]">u32</error> {
            #![start]
            0
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test param count mismatch E0132`() = checkErrors("""
        #![feature(start)]
        #[start]
        fn <error descr="Functions with a `start` attribute must have the following signature: `fn(isize, *const *const u8) -> isize` [E0132]">test_name</error>(_argc: isize, _argv: *const *const u8, foo: bool) -> isize {
            0
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test 1st param mismatch E0132`() = checkErrors("""
        #![feature(start)]
        #[start]
        fn lets_go(_argc: <error descr="Functions with a `start` attribute must have `isize` as first parameter [E0132]">usize</error>, _argv: *const *const u8) -> isize { 0 }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test 2nd param mismatch E0132`() = checkErrors("""
        #![feature(start)]
        #[start]
        fn lets_go(_argc: isize, _argv: <error descr="Functions with a `start` attribute must have `*const *const u8` as second parameter [E0132]">*const *const bool</error>) -> isize { 0 }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test all params mismatch E0132`() = checkErrors("""
        #![feature(start)]
        #[start]
        fn lets_go(_argc: <error descr="Functions with a `start` attribute must have `isize` as first parameter [E0132]">usize</error>, _argv: <error descr="Functions with a `start` attribute must have `*const *const u8` as second parameter [E0132]">*const *const bool</error>) -> isize { 0 }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test valid E0132`() = checkErrors("""
        #![feature(start)]
        #[start]
        fn valid(_argc: isize, _argv: *const *const u8) -> isize { 0 }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test valid E0132 with normalizable associated type`() = checkErrors("""
        #![feature(start)]

        struct IsizeStruct;
        struct ConstConstU8Struct;
        trait Trait { type Item; }
        impl Trait for IsizeStruct { type Item = isize; }
        impl Trait for ConstConstU8Struct { type Item = *const *const u8; }

        #[start]
        fn valid(_argc: <IsizeStruct as Trait>::Item, _argv: <ConstConstU8Struct as Trait>::Item) -> <IsizeStruct as Trait>::Item { 0 }
    """)


    @MockRustcVersion("1.0.0-nightly")
    fun `test missing feature E0132`() = checkErrors("""
        #[<error descr="#[start] function is experimental [E0658]">start</error>]
        fn valid(_argc: isize, _argv: *const *const u8) -> isize { 0 }
    """)

    fun `test inclusive range with no end E0586`() = checkErrors("""
        fn foo() {
            let x = 1<error descr="inclusive ranges must be bounded at the end (`..=b` or `a..=b`) [E0586]">..=</error>;
        }
    """)

    fun `test dot dot dot range with end`() = checkErrors("""
        fn foo() {
            let x = 1<error descr="`...` syntax is deprecated. Use `..` for an exclusive range or `..=` for an inclusive range">...</error>2;
        }
    """)

    fun `test dot dot dot range with no end`() = checkErrors("""
        fn foo() {
            let x = 1<error descr="`...` syntax is deprecated. Use `..` for an exclusive range or `..=` for an inclusive range">...</error>;;
        }
    """)

    fun `test inclusive range with end E0586`() = checkErrors("""
        fn foo() {
            let x = 1..=2;
        }
    """)

    fun `test range with no end E0586`() = checkErrors("""
        fn foo() {
            let x = 1..;
        }
    """)

    fun `test range with end E0586`() = checkErrors("""
        fn foo() {
            let x = 1..2;
        }
    """)

    @MockRustcVersion("1.66.0")
    fun `test inclusive range pat with no end E0586`() = checkErrors("""
        fn foo() {
            match 0 {
                 ..   => {},
                1..   => {},
                 <error>..2</error>  => {},
                <error>1..2</error>  => {},
                 ..=  => {},
                1<error descr="inclusive ranges must be bounded at the end (`..=b` or `a..=b`) [E0586]">..=</error>  => {},
                 ..=2 => {},
                1..=2 => {},
                 ...  => {},
                1<error descr="inclusive ranges must be bounded at the end (`..=b` or `a..=b`) [E0586]">...</error>  => {},
                 ...2 => {},
                1...2 => {},
            }
        }
    """)

    @MockRustcVersion("1.65.0")
    fun `test half open range patterns E0658 1`() = checkErrors("""
        fn foo() {
            match 0 {
                 ..   => {},
                1..   => {},
                 <error descr="half-open range patterns is experimental [E0658]"><error>..2</error></error>  => {},
                <error>1..2</error>  => {},
                 ..=  => {},
                1<error>..=</error>  => {},
                 <error descr="half-open range patterns is experimental [E0658]">..=2</error> => {},
                1..=2 => {},
                 ...  => {},
                1<error>...</error>  => {},
                 <error descr="half-open range patterns is experimental [E0658]">...2</error> => {},
                1...2 => {},
            }
        }
    """)

    @MockRustcVersion("1.65.0-nightly")
    fun `test half open range patterns E0658 2`() = checkErrors("""
        #![feature(half_open_range_patterns)]
        fn foo() {
            match 0 {
                 ..   => {},
                1..   => {},
                 <error>..2</error>  => {},
                <error>1..2</error>  => {},
                 ..=  => {},
                1<error>..=</error>  => {},
                 ..=2 => {},
                1..=2 => {},
                 ...  => {},
                1<error>...</error>  => {},
                 ...2 => {},
                1...2 => {},
            }
        }
    """)

    @MockRustcVersion("1.11.0")
    fun `test exclusive range patterns E0658 1`() = checkErrors("""
        fn foo() {
            match 0 {
                 ..   => {},
                1..   => {},
                 <error descr="exclusive range patterns is experimental [E0658]"><error>..2</error></error>  => {},
                <error descr="exclusive range patterns is experimental [E0658]">1..2</error>  => {},
                 ..=  => {},
                1<error>..=</error>  => {},
                 <error>..=2</error> => {},
                1..=2 => {},
                 ...  => {},
                1<error>...</error>  => {},
                 <error>...2</error> => {},
                1...2 => {},
            }
        }
    """)

    @MockRustcVersion("1.11.0-nightly")
    fun `test exclusive range patterns E0658 2`() = checkErrors("""
        #![feature(exclusive_range_pattern)]
        fn foo() {
            match 0 {
                 ..   => {},
                1..   => {},
                 <error>..2</error>  => {},
                1..2  => {},
                 ..=  => {},
                1<error>..=</error>  => {},
                 <error>..=2</error> => {},
                1..=2 => {},
                 ...  => {},
                1<error>...</error>  => {},
                 <error>...2</error> => {},
                1...2 => {},
            }
        }
    """)

    @MockRustcVersion("1.70.0")
    fun `test arbitrary enum discriminant without repr E0732`() = checkErrors("""
        #![feature(arbitrary_enum_discriminant)]
        enum <error descr="`#[repr(inttype)]` must be specified [E0732]">Enum</error> {
            Unit = 1,
            Tuple() = 2,
            Struct{} = 3,
        }
    """)

    @MockRustcVersion("1.70.0")
    fun `test valid arbitrary enum discriminant E0732`() = checkErrors("""
        #![feature(arbitrary_enum_discriminant)]
        #[repr(u8)]
        enum Enum {
            Unit = 3,
            Tuple(u16) = 2,
            Struct {
                a: u8,
                b: u16,
            } = 1,
        }
    """)

    @MockRustcVersion("1.56.0-nightly")
    @WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test non-structural match type as const generic parameter E0741`() = checkErrors("""
        #![feature(adt_const_params)]
        struct A;
        #[derive(PartialEq)]
        struct B;
        #[derive(Eq)]
        struct C;
        #[derive(PartialEq, Eq)]
        struct D;
        struct S<
            const P1: <error descr="A doesn't derive both `PartialEq` and `Eq` [E0741]">A</error>,
            const P2: <error descr="B doesn't derive both `PartialEq` and `Eq` [E0741]">B</error>,
            const P3: <error descr="C doesn't derive both `PartialEq` and `Eq` [E0741]">C</error>,
            const P4: D
        >;
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test non-structural match type as const generic parameter E0741 (proc macros are disabled)`() = checkErrors("""
        #![feature(adt_const_params)]
        struct A;
        struct S<const P: A>;
    """)

    @MockRustcVersion("1.37.0-nightly")
    fun `test arbitrary enum discriminant without feature`() = checkErrors("""
        #[repr(isize)]
        enum Enum {
            Unit = 1,
            Tuple() = <error descr="discriminant on a non-unit variant is experimental [E0658]">2</error>,
            Struct{} = <error descr="discriminant on a non-unit variant is experimental [E0658]">3</error>,
        }
    """)

    @MockRustcVersion("1.37.0-nightly")
    fun `test arbitrary enum discriminant with feature`() = checkErrors("""
        #![feature(arbitrary_enum_discriminant)]
        #[repr(isize)]
        enum Enum {
            Unit = 1,
            Tuple() = 2,
            Struct{} = 3,
        }
    """)


    fun `test break in closure E0267`() = checkErrors("""
        fn foo() {
            let x = || { <error descr="`break` cannot be used in closures, only inside `loop` and `while` blocks [E0267]">break</error>; };
        }
    """)

    fun `test continue in closure E0267`() = checkErrors("""
        fn foo() {
            let x = || { <error descr="`continue` cannot be used in closures, only inside `loop` and `while` blocks [E0267]">continue</error>; };
        }
    """)

    fun `test break without loop E0268`() = checkErrors("""
        fn foo() {
            <error descr="`break` may only be used inside `loop` and `while` blocks [E0268]">break</error>;
        }
    """)

    fun `test continue without loop E0268`() = checkErrors("""
        fn foo() {
            <error descr="`continue` may only be used inside `loop` and `while` blocks [E0268]">continue</error>;
        }
    """)


    fun `test break in loop E0267, E0268`() = checkErrors("""
        fn foo() {
            loop {
                break;
            }
        }
    """)

    fun `test break in while loop E0267, E0268`() = checkErrors("""
        fn foo() {
            while true {
                break;
            }
        }
    """)

    fun `test break in for loop E0267, E0268`() = checkErrors("""
        fn foo() {
            for _ in 0..3 {
                break;
            }
        }
    """)


    fun `test continue in loop E0267, E0268`() = checkErrors("""
        fn foo() {
            loop {
                continue;
            }
        }
    """)

    fun `test continue in while loop E0267, E0268`() = checkErrors("""
        fn foo() {
            while true {
                continue;
            }
        }
    """)

    fun `test continue in for loop E0267, E0268`() = checkErrors("""
        fn foo() {
            for _ in 0..3 {
                continue;
            }
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test break in block with label E0268`() = checkErrors("""
        #![feature(label_break_value)]
        fn main() {
            let a = 'foo: {
                break 'foo 1;
            };
            println!("{}", a);
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test continue in block with label E0268`() = checkErrors("""
        #![feature(label_break_value)]
        fn main() {
            let a = 'foo: {
                continue 'foo; // This test should not report E0268; compiler reports E0696 instead
            };
            println!("{}", a);
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test nested blocks E0268`() = checkErrors("""
       #![feature(label_break_value)]

        fn main() {
            let a = 'a: {
                let b = {
                    if true {
                        break 'a 123
                    } else {
                        123
                    }
                };
                b
            };
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test closure inside loop E0268`() = checkErrors("""
        #![feature(label_break_value)]

        fn main() {
            for i in 1..10 {
                let a = |x: i32| <error descr="`break` cannot be used in closures, only inside `loop` and `while` blocks [E0267]">break</error>;
                1
            };
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    fun `test loop inside closure E0268`() = checkErrors("""
        #![feature(label_break_value)]

        fn main() {
            || {
                for i in 1..10 {
                    break;
                }
            };
        }
    """)

    @MockRustcVersion("1.35.0")
    fun `test param attrs E0658 1`() = checkErrors("""
        struct S;
        fn f1(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> x: S) {}
        fn f2(<error descr="attributes on function parameters is experimental [E0658]">#[attr1] #[attr2]</error> x: S) {}
        impl S {
            fn f3(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> self) {}
            fn f4(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> &self) {}
            fn f5<'a>(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> &mut self) {}
            fn f6<'a>(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> &'a self) {}
            fn f7<'a>(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> &'a mut self, <error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> x: S, y: S) {}
            fn f8(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> self: Self) {}
            fn f9(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> self: S<Self>) {}
        }
        trait T { fn f10(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> S); }
        extern "C" { fn f11(<error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> x: S, <error descr="attributes on function parameters is experimental [E0658]">#[attr]</error> ...); }
    """)

    @MockRustcVersion("1.36.0-nightly")
    fun `test param attrs E0658 2`() = checkErrors("""
        #![feature(param_attrs)]
        struct S;
        fn f1(#[attr] x: S) {}
        fn f2(#[attr1] #[attr2] x: S) {}
        impl S {
            fn f3(#[attr] self) {}
            fn f4(#[attr] &self) {}
            fn f5<'a>(#[attr] &mut self) {}
            fn f6<'a>(#[attr] &'a self) {}
            fn f7<'a>(#[attr] &'a mut self, #[attr] x: S, y: S) {}
            fn f8(#[attr] self: Self) {}
            fn f9(#[attr] self: S<Self>) {}
        }
        trait T { fn f10(#[attr] S); }
        extern "C" { fn f11(#[attr] x: S, #[attr] ...); }
    """)

    fun `test no errors when correct field amount in tuple struct`() = checkErrors("""
        struct Foo (i32, i32, i32);

        fn main() {
            let Foo (a, b, c) = foo;
        }
    """)

    fun `test no errors when correct field amount in tuple struct with rest pat`() = checkErrors("""
        struct Foo (i32, i32, i32);

        fn main() {
            let foo = Foo(1,2,3);
            let Foo (a, b, c, ..) = foo;
        }
    """)

    fun `test missing fields in tuple struct`() = checkErrors("""
        struct Foo (i32, i32, i32);

        fn main() {
            let <error descr="Tuple struct pattern does not correspond to its declaration: expected 3 fields, found 2 [E0023]">Foo (a, b)</error> = foo;
        }
    """)

    fun `test extra fields in tuple struct`() = checkErrors("""
        struct Foo (i32, i32, i32);

        fn main() {
            let <error descr="Extra fields found in the tuple struct pattern: expected 3, found 4 [E0023]">Foo (a, b, c, d)</error> = foo;
        }
    """)

    fun `test extra fields in tuple struct with rest pat`() = checkErrors("""
        struct Foo (i32, i32, i32);

        fn main() {
            let <error descr="Extra fields found in the tuple struct pattern: expected 3, found 4 [E0023]">Foo (a, b, c, d, ..)</error> = foo;
        }
    """)

    fun `test wrong field name`() = checkErrors("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }

        fn main() {
            let <error descr="Struct pattern does not mention field `c` [E0027]">Foo { a, b, <error descr="Extra field found in the struct pattern: `d` [E0026]">d</error> }</error> = foo;
        }
    """)

    fun `test wrong field name with dots`() = checkErrors("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }

        fn main() {
            let Foo { <error>d</error>, a, .. } = foo;
        }
    """)

    fun `test wrong field name in enum variant pattern`() = checkErrors("""
        enum Foo {
            Bar { quux: i32, spam: i32 },
            Baz(i32, i32),
        }

        fn f(foo: Foo) {
            match foo {
                <error descr="Enum variant pattern does not mention fields `quux`, `spam` [E0027]">Foo::Bar { <error descr="Extra field found in the struct pattern: `abc` [E0026]">abc</error> }</error> => {},
                <error descr="Enum variant pattern does not correspond to its declaration: expected 2 fields, found 1 [E0023]">Foo::Baz(a)</error> => {},
            }
        }
    """)

    fun `test no error in mixed up fields`() = checkErrors("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }

        fn main() {
            let Foo { b, c, a } = foo;
        }
    """)

    fun `test no error in mixed up fields with dots`() = checkErrors("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }

        fn main() {
            let Foo { c, b, .. } = foo;
        }
    """)

    fun `test no error in mixed up fields with extra dots`() = checkErrors("""
        struct Foo {
            a: i32,
            b: i32,
            c: i32,
        }

        fn main() {
            let Foo { c, b, a, .. } = foo;
        }
    """)

    fun `test no error on missing field under disabled cfg-attribute`() = checkErrors("""
        struct Foo {
            a: i32,
            b: i32,
            #[cfg(intellij_rust)]
            c: i32,
        }

        fn main() {
            let Foo { a, b } = foo;
        }
    """)

    fun `test no error in tuple with dots in the middle`() = checkErrors("""
        struct Foo(i32, i32, i32);

        fn main() {
            let Foo (a, .., b) = foo;
        }
    """)

    fun `test no error in tuple with underscores`() = checkErrors("""
        struct Foo(i32, i32, i32);

        fn main() {
            let Foo (_, _, _) = foo;
        }
    """)

    fun `test no E0026 on raw identifier field`() = checkErrors("""
        struct Foo { r#field: u64 }
        fn bar(f: Foo) {
            let Foo { r#field: _ } = f;
        }
    """)

    @MockRustcVersion("1.40.0-nightly")
    fun `test unstable feature E0658 1`() = checkErrors("""
        mod a {
            #[unstable(feature = "aaa")]
            pub struct S {
                #[unstable(feature = "bbb", reason = "foo")] pub field: i32
            }
        }

        use a::<error descr="`aaa` is unstable [E0658]">S</error>;

        #[unstable(feature = "ddd")]
        impl <error descr="`aaa` is unstable [E0658]">S</error> {
            #[unstable(feature = "ccc", reason = "bar \
                baz")]
            fn foo(self) -> Self { unreachable!() }
        }

        fn main() {
            let x = <error descr="`aaa` is unstable [E0658]">S</error> { field: 0 };
            x.<error descr="`bbb` is unstable: foo [E0658]">field</error>;
            x.<error descr="`ccc` is unstable: bar baz [E0658]">foo</error>();
        }
    """)

    @MockRustcVersion("1.40.0-nightly")
    fun `test unstable feature E0658 2`() = checkErrors("""
        #![feature(aaa)]
        #![feature(bbb)]
        #![feature(ccc)]
        #![feature(ddd)]

        mod a {
            #[unstable(feature = "aaa")]
            pub struct S {
                #[unstable(feature = "bbb", reason = "foo")] pub field: i32
            }
        }

        use a::S;

        #[unstable(feature = "ddd")]
        impl S {
            #[unstable(feature = "ccc", reason = "bar \
                baz")]
            fn foo(self) -> Self { unreachable!() }
        }

        fn main() {
            let x = S { field: 0 };
            x.field;
            x.foo();
        }
    """)

    fun `test no E0603 for module with multiple declarations`() = checkDontTouchAstInOtherFiles("""
    //- main.rs
        mod foo;
        mod bar;
        use foo::S; // error was here
    //- foo.rs
        pub struct S;
    //- bar.rs
        #[path = "foo.rs"] mod foo1;
    """)

    fun `test E0603 for module with multiple declarations`() = checkDontTouchAstInOtherFiles("""
    //- main.rs
        use crate::foo::bar::x; // TODO no error here although compiler produces it

        mod foo {
            mod bar;
        }

        fn main() {}

    //- lib.rs
        use crate::foo::bar::x;

        mod foo {
            pub mod bar;
        }

    //- foo/bar.rs
        pub fn x() {}
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no E0603 for module with multiple declarations under cfg attributes`() = checkByFileTree("""
    //- lib.rs
        #[cfg(not(intellij_rust))]
        mod foo;
        #[cfg(intellij_rust)]
        pub mod foo;
    //- foo.rs
        pub fn foo() {}
    //- main.rs
        extern crate test_package;
        use test_package::foo::bar;/*caret*/
    """)

    fun `test for union pattern missing fields`() = checkErrors("""
        union TestUnion { i: i32, f: f32 }

        fn test_fun(tu: TestUnion) {
            unsafe {
                match tu {
                    TestUnion { i: i32 } => {}
                    <error descr="Union patterns requires a field">TestUnion { }</error> => {}
                }
            }
        }
    """)

    fun `test too many union pattern fields`() = checkErrors("""
        union TestUnion { i: i32, f: f32 }

        fn test_fun(tu: TestUnion) {
            unsafe {
                match tu {
                    <error descr="Union patterns should have exactly one field">TestUnion { i, f }</error> => {}
                }
            }
        }
    """)

    fun `test for fields that do not exist in the union pattern`() = checkErrors("""
        union TestUnion { i: i32, f: f32 }

        fn test_fun(tu: TestUnion) {
            unsafe {
                match tu {
                    TestUnion { <error descr="Extra field found in the union pattern: `x` [E0026]">x</error> } => {}
                }
            }
        }
    """)

    fun `test for union pattern with too many and non-existent fields`() = checkErrors("""
        union TestUnion { i: i32, f: f32 }

        fn test_fun(tu: TestUnion) {
            unsafe {
                match tu {
                    <error descr="Union patterns should have exactly one field">TestUnion { i, f, <error descr="Extra field found in the union pattern: `x` [E0026]">x</error> }</error> => {}
                }
            }
        }
    """)

    fun `test E0435 non-constant in array type`() = checkErrors("""
        fn main() {
            let foo = 42;
            let a: [u8; <error descr="A non-constant value was used in a constant expression [E0435]">foo</error>];
        }
    """)

    fun `test E0435 non-constant in binary expr in array type`() = checkErrors("""
        fn main() {
            const A: usize = 1;
            let b: usize = 1;
            const C: usize = 1;
            let d: usize = 1;
            let a: [u8; A + <error descr="A non-constant value was used in a constant expression [E0435]">b</error> + C + <error descr="A non-constant value was used in a constant expression [E0435]">d</error>];
        }
    """)

    fun `test E0435 non-constant in const function in array type`() = checkErrors("""
        fn main() {
            const fn foo(a: usize, b: usize) -> usize {
                a + b
            }
            let bar: usize = 42;
            let a: [u8; foo(1, <error descr="A non-constant value was used in a constant expression [E0435]">bar</error>)];
        }
    """)

    fun `test E0435 non-constant in method call in array type`() = checkErrors("""
        struct Foo { x: usize }

        impl Foo {
            fn bar(&self) -> usize {
                self.x + self.x
            }
        }

        fn main() {
            let foo = Foo { x: 1 };
            let a: [u8; <error descr="A non-constant value was used in a constant expression [E0435]">foo</error>.bar()];
        }
    """)

    fun `test E0435 non-constant in array expr`() = checkErrors("""
        fn main() {
            let c = 100;
            let _: [i32; 100] = [0; <error descr="A non-constant value was used in a constant expression [E0435]">c</error>];
        }
    """)

    fun `test no E0435 literal in array type`() = checkErrors("""
        fn main() {
            let a: [u8; 42];
        }
    """)

    fun `test no E0435 constant in array type`() = checkErrors("""
        fn main() {
            const C: usize = 42;
            let a: [u8; C];
        }
    """)

    fun `test no E0435 const function in array type`() = checkErrors("""
        fn main() {
            const fn foo(a: usize, b: usize) -> usize {
                a + b
            }
            const BAR: usize = 42;
            let a: [u8; foo(BAR, 1)];
        }
    """)

    fun `test no E0435 constant in array expr`() = checkErrors("""
        fn main() {
            const C: usize = 100;
            let _: [i32; 100] = [0; C];
        }
    """)

    fun `test no E0015 in array with const parameter`() = checkErrors("""
        fn new_array<const N: usize>() -> [u8; N] {
            [0; N]
        }
    """)

    fun `test E0015 in array type`() = checkErrors("""
        fn main() {
            fn foo(a: usize, b: usize) -> usize {
                a + b
            }
            let a: [u8; <error descr="Calls in constants are limited to constant functions, tuple structs and tuple variants [E0015]">foo</error>(1, 2)];
        }
    """)

    fun `test E0015 and E0435 in array type`() = checkErrors("""
        fn foo(a: usize, b: usize) -> usize {
            a + b
        }
        fn main() {
            let bar: usize = 42;
            let a: [u8; <error descr="Calls in constants are limited to constant functions, tuple structs and tuple variants [E0015]">foo</error>(1, <error descr="A non-constant value was used in a constant expression [E0435]">bar</error>)];
        }
    """)

    @MockRustcVersion("1.0.0-nightly")
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test custom start proc macro attr`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn start(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        #![feature(start)]
        extern crate dep_proc_macro;

        use dep_proc_macro::start;

        #[<error descr="Start attribute can be placed only on functions [E0132]">start/*caret*/</error>]
        type Foo = i32;
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test custom inline proc macro attr`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn inline(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        extern crate dep_proc_macro;

        use dep_proc_macro::inline;

        #[<error descr="Attribute should be applied to function or closure [E0518]">inline/*caret*/</error>]
        type Foo = i32;
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test custom inline proc macro attr use alias`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn inline(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        extern crate dep_proc_macro;

        use dep_proc_macro::inline as repr;

        #[repr/*caret*/]
        type Foo = i32;
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test custom inline proc macro attr and disable cfg`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn inline(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        extern crate dep_proc_macro;

        #[cfg(target_os = "windows")]
        use dep_proc_macro::inline;

        #[<error descr="Attribute should be applied to function or closure [E0518]">inline/*caret*/</error>]
        type Foo = i32;
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test custom inline proc macro attr but ref invalid`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn inline(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        extern crate dep_proc_macro;

        use dep_proc_macro::test::inline;

        #[<error descr="Attribute should be applied to function or closure [E0518]">inline/*caret*/</error>]
        type Foo = i32;
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test custom inline proc macro attr but at the child level`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn inline(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        extern crate dep_proc_macro;

        #[<error descr="Attribute should be applied to function or closure [E0518]">inline</error>]
        type Foo = i32;

        fn foo() {
            use dep_proc_macro::inline;

            #[<error descr="Attribute should be applied to function or closure [E0518]">inline/*caret*/</error>]
            struct Test(i32);
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test custom inline proc macro attr but declared in a child level`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn inline(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        extern crate dep_proc_macro;

        #[<error descr="Attribute should be applied to function or closure [E0518]">inline/*caret*/</error>]
        type Foo = i32;

        fn foo() {
            use dep_proc_macro::inline;
        }
    """)

    fun `test use derive attr on unsupported items`() = checkErrors("""
        <error descr="`derive` may only be applied to structs, enums and unions [E0774]">#[derive(Debug)]</error>
        type Test = i32;
    """)

    fun `test use derive attr on supported items`() = checkErrors("""
        #[derive(Debug)]
        struct Test(i32);

        #[derive(Debug)]
        enum Color {
            RED, GREEN
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test user defined derive proc macro`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn derive(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- lib.rs
        /*caret*/
        #[dep_proc_macro::derive]
        fn main() {}

        mod foo {
            use dep_proc_macro::derive;

            #[derive]
            fn bar() {}
        }
    """)

    fun `test E0025 struct field bound multiple times`() = checkErrors("""
        struct Foo { a: i32, b: i32 }

        fn foo(x: Foo) {
            let Foo { a, <error descr="Field `a` bound multiple times in the pattern [E0025]"><error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error></error>, b } = x;
        }
    """)

    fun `test E0025 renamed struct field bound multiple times`() = checkErrors("""
        struct Foo { a: i32, b: i32 }

        fn foo(x: Foo) {
            let Foo { a, <error descr="Field `a` bound multiple times in the pattern [E0025]">a: c</error>, b } = x;
        }
    """)

    fun `test E0025 struct field bound multiple times in nested pattern`() = checkErrors("""
        struct Foo { c: i32, b: Bar }
        struct Bar { a: i32, b: i32 }

        fn foo(x: Foo) {
            let Foo { c, b: Bar { a, <error descr="Field `a` bound multiple times in the pattern [E0025]"><error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error></error>, .. } } = x;
        }
    """)

    fun `test E0416 identifier bound multiple times in struct pattern`() = checkErrors("""
        struct Foo { a: i32, b: i32 }

        fn foo(x: Foo) {
            let Foo { a, b: <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error> } = x;
        }
    """)

    fun `test E0416 identifier bound multiple times in nested struct pattern`() = checkErrors("""
        struct Foo { a: Bar, b: Bar }
        struct Bar { a: i32, b: i32 }

        fn foo(x: Foo) {
            let Foo { a: Bar { a, b }, b: Bar { <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error>, <error descr="Identifier `b` is bound more than once in the same pattern [E0416]">b</error> } } = x;
        }
    """)

    fun `test E0416 identifier bound multiple times in nested complex pattern`() = checkErrors("""
        struct Foo { a: Bar, b: Bar }
        struct Bar { a: i32, b: (i32, i32) }

        fn foo(x: Foo) {
            let Foo { a: Bar { a, b }, b: Bar { <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error>, b: (<error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error>, <error descr="Identifier `b` is bound more than once in the same pattern [E0416]">b</error>) } } = x;
        }
    """)

    fun `test E0416 identifier bound multiple times in tuple pattern`() = checkErrors("""
        fn foo() {
            let (a, <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error>) = (0, 0);
        }
    """)

    fun `test E0416 identifier bound multiple times in tuple struct pattern`() = checkErrors("""
        struct Foo(u32, u32);

        fn foo(x: Foo) {
            let Foo (a, <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error>) = x;
        }
    """)

    fun `test E0416 in or pattern branch`() = checkErrors("""
        enum E {
            Bar { a: u32, b: u32 },
            Baz { a: u32 }
        }

        fn foo(x: E) {
            match x {
                E::Bar { a, b: <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error> } | E::Baz { a } => {}
            }
        }
    """)

    fun `test no E0416 on or pattern in distinct or branches`() = checkErrors("""
        enum E {
            Bar { a: u32 },
            Baz { a: u32 }
        }

        fn foo(x: E) {
            match x {
                E::Bar { a } | E::Baz { a } => {}
            }
        }
    """)

    @MockRustcVersion("1.38.0-nightly")
    fun `test E0416 in nested or pattern branches`() = checkErrors("""
        #![feature(or_patterns)]

        struct Foo { a: i32, b: E }

        enum E {
            Bar { a: u32 },
            Baz { a: u32 }
        }

        fn foo(x: Foo) {
            let Foo { a, b: E::Bar { <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error> } | E::Baz { <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error> } } = x;
        }
    """)

    @MockRustcVersion("1.38.0-nightly")
    fun `test E0416 in nested or pattern branches in reverse order`() = checkErrors("""
        #![feature(or_patterns)]

        struct Foo { a: i32, b: E }

        enum E {
            Bar { a: u32 },
            Baz { a: u32 }
        }

        fn foo(x: Foo) {
            let Foo { b: E::Bar { a } | E::Baz { a }, <error descr="Identifier `a` is bound more than once in the same pattern [E0416]">a</error> } = x;
        }
    """)

    fun `test no E0416 with path pattern`() = checkErrors("""
        enum Option {
            None,
            Some
        }
        use Option::*;

        fn foo(x: (Option, Option)) {
            match x {
                (None, None) => {},
                _ => {}
            }
        }
    """)

    fun `test empty function E0308 wrong return type`() = checkByText("""
        fn foo() -> u32 {<error descr="mismatched types [E0308]">}</error>
    """)

    fun `test empty function ignore impl trait return type`() = checkByText("""
        trait FooBar {}
        impl FooBar for () {}

        fn foo() -> impl FooBar {}
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test E0116 inherent impls should be in same crate`() = checkByFileTree("""
    //- lib.rs
        pub struct ForeignStruct {}
        pub trait ForeignTrait {}
    //- main.rs
        /*caret*/
        use test_package::{ForeignStruct, ForeignTrait};

        struct LocalStruct {}
        trait LocalTrait {}
        type ForeignStructAlias = ForeignStruct;
        type LocalStructAlias = LocalStruct;

        impl <error descr="Cannot define inherent `impl` for a type outside of the crate where the type is defined [E0116]">ForeignStruct</error> {}
        impl <error descr="Cannot define inherent `impl` for a type outside of the crate where the type is defined [E0116]">ForeignStructAlias</error> {}
        impl LocalStruct {}
        impl LocalStructAlias {}

        impl dyn LocalTrait {}
        impl dyn LocalTrait + Send {}
        impl dyn Send + LocalTrait {}
        impl <error descr="Cannot define inherent `impl` for a type outside of the crate where the type is defined [E0116]">dyn ForeignTrait</error> {}
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test E0117 trait impls orphan rules`() = checkByFileTree("""
    //- lib.rs
        pub struct ForeignStruct {}
        pub trait ForeignTrait {}
        pub trait ForeignTrait0 {}
        pub trait ForeignTrait1<T> {}
    //- main.rs
        /*caret*/
        use std::pin::Pin;
        use test_package::*;

        pub struct LocalStruct {}
        pub trait LocalTrait {}

        // simple
        impl LocalTrait for LocalStruct {}
        impl LocalTrait for ForeignStruct {}
        impl ForeignTrait for LocalStruct {}
        impl <error descr="Only traits defined in the current crate can be implemented for arbitrary types [E0117]">ForeignTrait</error> for ForeignStruct {}

        // trait has type parameters
        impl ForeignTrait1<LocalStruct> for ForeignStruct {}
        impl ForeignTrait1<ForeignStruct> for LocalStruct {}
        impl <error descr="Only traits defined in the current crate can be implemented for arbitrary types [E0117]">ForeignTrait1<ForeignStruct></error> for ForeignStruct {}

        // uncovering
        impl ForeignTrait for &LocalStruct {}
        impl <error descr="Only traits defined in the current crate can be implemented for arbitrary types [E0117]">ForeignTrait</error> for &ForeignStruct {}
        impl ForeignTrait for Box<LocalStruct> {}
        impl <error descr="Only traits defined in the current crate can be implemented for arbitrary types [E0117]">ForeignTrait</error> for Box<ForeignStruct> {}
        impl ForeignTrait for Pin<LocalStruct> {}
        impl <error descr="Only traits defined in the current crate can be implemented for arbitrary types [E0117]">ForeignTrait</error> for Pin<ForeignStruct> {}

        // trait objects
        impl ForeignTrait for Box<dyn LocalTrait + Send> {}
        impl ForeignTrait for Box<dyn Send + LocalTrait> {}
        impl <error descr="Only traits defined in the current crate can be implemented for arbitrary types [E0117]">ForeignTrait</error> for Box<dyn ForeignTrait0 + Send> {}
        impl <error descr="Only traits defined in the current crate can be implemented for arbitrary types [E0117]">ForeignTrait</error> for Box<dyn Send + ForeignTrait0> {}
        impl <error descr="Only traits defined in the current crate can be implemented for arbitrary types [E0117]">ForeignTrait</error> for Box<dyn Send> {}
    """)

    fun `test invalid ABI E0703`() = checkErrors("""
        extern fn extern_fn() {}
        extern "C" fn extern_c_fn() {}
        extern "R\x75st" fn extern_fn_with_escape_in_abi() {}
        extern r"system" fn extern_fn_with_raw_abi() {}
        extern <error descr="Invalid ABI: found invalid [E0703]">"invalid"</error> fn extern_fn_with_invalid_abi_name() {}
    """)

    fun `test invalid ABI E0703 suggestion fix`() = checkFixByText("Change to `cdecl`", """
        extern <error descr="Invalid ABI: found cdelc [E0703]">"cdelc"/*caret*/</error> fn extern_fn() {}
    """, """
        extern "cdecl"/*caret*/ fn extern_fn() {}
    """)

    @MockRustcVersion("1.54.0")
    fun `test experimental ABI E0658`() = checkErrors("""
        extern "Rust" fn fn1() {}
        extern "C" fn fn2() {}
        extern <error descr="C-unwind ABI is experimental [E0658]">"C-unwind"</error> fn fn3() {}
        extern "cdecl" fn fn4() {}
        extern "stdcall" fn fn5() {}
        extern <error descr="stdcall-unwind ABI is experimental [E0658]">"stdcall-unwind"</error> fn fn6() {}
        extern "fastcall" fn fn7() {}
        extern <error descr="vectorcall ABI is experimental [E0658]">"vectorcall"</error> fn fn8() {}
        extern <error descr="thiscall ABI is experimental [E0658]">"thiscall"</error> fn fn9() {}
        extern <error descr="thiscall-unwind ABI is experimental [E0658]">"thiscall-unwind"</error> fn fn10() {}
        extern "aapcs" fn fn11() {}
        extern "win64" fn fn12() {}
        extern "sysv64" fn fn13() {}
        extern <error descr="ptx-kernel ABI is experimental [E0658]">"ptx-kernel"</error> fn fn14() {}
        extern <error descr="msp430-interrupt ABI is experimental [E0658]">"msp430-interrupt"</error> fn fn15() {}
        extern <error descr="x86-interrupt ABI is experimental [E0658]">"x86-interrupt"</error> fn fn16() {}
        extern <error descr="amdgpu-kernel ABI is experimental [E0658]">"amdgpu-kernel"</error> fn fn17() {}
        extern <error descr="efiapi ABI is experimental [E0658]">"efiapi"</error> fn fn18() {}
        extern <error descr="avr-interrupt ABI is experimental [E0658]">"avr-interrupt"</error> fn fn19() {}
        extern <error descr="avr-non-blocking-interrupt ABI is experimental [E0658]">"avr-non-blocking-interrupt"</error> fn fn20() {}
        extern <error descr="C-cmse-nonsecure-call ABI is experimental [E0658]">"C-cmse-nonsecure-call"</error> fn fn21() {}
        extern <error descr="wasm ABI is experimental [E0658]">"wasm"</error> fn fn22() {}
        extern "system" fn fn23() {}
        extern <error descr="system-unwind ABI is experimental [E0658]">"system-unwind"</error> fn fn24() {}
        extern <error descr="rust-intrinsic ABI is experimental [E0658]">"rust-intrinsic"</error> fn fn25() {}
        extern <error descr="rust-call ABI is experimental [E0658]">"rust-call"</error> fn fn26() {}
        extern <error descr="platform-intrinsic ABI is experimental [E0658]">"platform-intrinsic"</error> fn fn27() {}
        extern <error descr="unadjusted ABI is experimental [E0658]">"unadjusted"</error> fn fn28() {}
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test add feature for experimental ABI E0658`() = checkFixByText("Add `abi_x86_interrupt` feature", """
        extern <error descr="x86-interrupt ABI is experimental [E0658]">"x86-interrupt"/*caret*/</error> fn extern_fn() {}
    """, """
        #![feature(abi_x86_interrupt)]

        extern "x86-interrupt"/*caret*/ fn extern_fn() {}
    """, preview = null)

    fun `test edition 2015 keyword as lifetime name`() = checkErrors("""
        struct Me<<error descr="Lifetimes cannot use keyword names">'type</error>> {
            name: &<error descr="Lifetimes cannot use keyword names">'type</error> str,
        }
    """)

    fun `test edition 2018 keyword as lifetime name`() = checkErrors("""
        struct Me<<error descr="Lifetimes cannot use keyword names">'async</error>> {
            name: &<error descr="Lifetimes cannot use keyword names">'async</error> str,
        }
    """)

    @MockEdition(Edition.EDITION_2015)
    fun `test use edition 2018 keyword as lifetime name in the edition 2015`() = checkErrors("""
        struct Me<'async>  {
            name: &'async str,
        }
    """)

    @MockRustcVersion("1.56.0")
    fun `test macro 2 is experimental 1`() = checkErrors("""
        pub <error descr="`macro` is experimental [E0658]">macro</error> id($ e:expr) {
            $ e
        }
    """)

    @MockRustcVersion("1.56.0-nightly")
    fun `test macro 2 is experimental 2`() = checkErrors("""
        #![feature(decl_macro)]

        pub macro id($ e:expr) {
            $ e
        }
    """)

    fun `test keyword as label name`() = checkErrors("""
        fn main() {
            let mut x = 0;
            <error descr="Invalid label name `'fn`">'fn</error>: while true {
                println!("hello");
                x = x + 1;
                if x == 100 {
                    break <error descr="Invalid label name `'fn`">'fn</error>;
                }
            }
        }
    """)

    @MockRustcVersion("1.23.0")
    fun `test extern types E0658 1`() = checkErrors("""
        extern { <error descr="extern types is experimental [E0658]">type ItemForeign;</error> }
    """)

    @MockRustcVersion("1.23.0-nightly")
    fun `test extern types E0658 2`() = checkErrors("""
        #![feature(extern_types)]
        extern { type ItemForeign; }
    """)

    @MockRustcVersion("1.23.0")
    fun `test generic associated types E0658 1`() = checkErrors("""
        struct S;
        type ItemFree<'a> where 'a : 'static = S;
        impl S { <error>type Item<error descr="generic associated types is experimental [E0658]"><'a></error> <error descr="where clauses on associated types is experimental [E0658]">where 'a : 'static</error> = S;</error> }
        trait T { type Item<error descr="generic associated types is experimental [E0658]"><'a></error> <error descr="where clauses on associated types is experimental [E0658]">where 'a : 'static</error>; }
        impl T for S { type Item<error descr="generic associated types is experimental [E0658]"><'a></error> <error descr="where clauses on associated types is experimental [E0658]">where 'a : 'static</error> = S; }
    """)

    @MockRustcVersion("1.60.0")
    fun `test generic associated types E0658 (new where clause location)`() = checkErrors("""
        struct S;
        type ItemFree<'a> = S where 'a : 'static;
        impl S { <error>type Item<error descr="generic associated types is experimental [E0658]"><'a></error> = S <error descr="where clauses on associated types is experimental [E0658]">where 'a : 'static</error>;</error> }
        trait T { type Item<error descr="generic associated types is experimental [E0658]"><'a></error> <error descr="where clauses on associated types is experimental [E0658]">where 'a : 'static</error>; }
        impl T for S { type Item<error descr="generic associated types is experimental [E0658]"><'a></error> = S <error descr="where clauses on associated types is experimental [E0658]">where 'a : 'static</error>; }
    """)

    @MockRustcVersion("1.23.0-nightly")
    fun `test generic associated types E0658 2`() = checkErrors("""
        #![feature(generic_associated_types)]
        struct S;
        type ItemFree<'a> where 'a : 'static = S;
        impl S { <error>type Item<'a> where 'a : 'static = S;</error> }
        trait T { type Item<'a> where 'a : 'static; }
        impl T for S { type Item<'a> where 'a : 'static = S; }
    """)

    @MockRustcVersion("1.68.0")
    fun `test generic associated types E0658 3`() = checkErrors("""
        struct S;
        type ItemFree<>;
        impl S { <error descr="inherent associated types is experimental [E0658]">type Item<>;</error> }
        trait T { type Item<>; }
        impl T for S { type Item<>; }
    """)

    @MockRustcVersion("1.52.0")
    fun `test inherent associated types E0658 1`() = checkErrors("""
        impl S { <error descr="inherent associated types is experimental [E0658]">type Item;</error> }
    """)

    @MockRustcVersion("1.52.0-nightly")
    fun `test inherent associated types E0658 2`() = checkErrors("""
        #![feature(inherent_associated_types)]
        impl S { type Item; }
    """)

    @MockRustcVersion("1.2.0")
    fun `test associated type defaults E0658 1`() = checkErrors("""
        struct S;
        type ItemFree = S;
        impl S { <error>type Item = S;</error> }
        trait T { type Item = <error descr="associated type defaults is experimental [E0658]">S</error>; }
        impl T for S { type Item = S; }
    """)

    @MockRustcVersion("1.2.0-nightly")
    fun `test associated type defaults E0658 2`() = checkErrors("""
        #![feature(associated_type_defaults)]
        struct S;
        type ItemFree = S;
        impl S { <error>type Item = S;</error> }
        trait T { type Item = S; }
        impl T for S { type Item = S; }
    """)

    @MockRustcVersion("1.0.0")
    fun `test unnecessary visibility qualifier E0449`() = checkErrors("""
        struct S;
        pub type ItemFree = S;
        impl S { <error descr="inherent associated types is experimental [E0658]">pub type Item = S;</error> }
        trait T { <error descr="Unnecessary visibility qualifier [E0449]">pub</error> type Item; }
        impl T for S { <error descr="Unnecessary visibility qualifier [E0449]">pub</error> type Item = S; }
        extern { <error descr="extern types is experimental [E0658]">pub type ItemForeign;</error> }
    """)

    fun `test do not annotate usage of private field in debugger code fragment`() = checkByCodeFragment("""
        mod my {
            pub struct Foo { inner: i32 }
        }
        fn bar(foo: my::Foo) {
            /*caret*/;
        }
    """, """foo.inner""", ::RsDebuggerExpressionCodeFragment)

    fun `test annotate usage of private field in expr code fragment`() = checkByCodeFragment("""
        mod my {
            pub struct Foo { inner: i32 }
        }
        fn bar(foo: my::Foo) {
            /*caret*/;
        }
    """, """foo.<error descr="Field `inner` of struct `my::Foo` is private [E0616]">inner</error>""", ::RsExpressionCodeFragment)

    @MockRustcVersion("1.49.0")
    fun `test inline const E0658 1`() = checkErrors("""
        fn foo(a: i32, b: i32) {}
        fn main() {
            <error descr="inline const is experimental [E0658]">const</error> { 0 };
            let x = <error descr="inline const is experimental [E0658]">const</error> { 1 };
            foo(<error descr="inline const is experimental [E0658]">const</error> { 2 }, <error descr="inline const is experimental [E0658]">const</error> { 3 });
        }
    """)

    @MockRustcVersion("1.49.0-nightly")
    fun `test inline const E0658 2`() = checkErrors("""
        #![feature(inline_const)]
        fn foo(a: i32, b: i32) {}
        fn main() {
            const { 0 };
            let x = const { 2 };
            foo(const { 3 }, const { 4 });
        }
    """)

    @MockRustcVersion("1.58.0")
    fun `test inline const pat E0658 1`() = checkErrors("""
        fn main() {
            match 0 {
                <error descr="inline const pat is experimental [E0658]">const</error> { 0 } => 0,
                <error descr="inline const pat is experimental [E0658]">const</error> { 1 } => 1,
                <error descr="inline const pat is experimental [E0658]">const</error> { 2 }..=<error descr="inline const pat is experimental [E0658]">const</error> { 3 } => 2,
                _ => unreachable!(),
            };
        }
    """)

    @MockRustcVersion("1.58.0-nightly")
    fun `test inline const pat E0658 2`() = checkErrors("""
        #![feature(inline_const_pat)]
        fn main() {
            match 0 {
                const { 0 } => 0,
                const { 1 } => 1,
                const { 2 }..=const { 3 } => 2,
                _ => unreachable!(),
            };
        }
    """)

    @MockRustcVersion("1.68.0")
    fun `test const closures E0658 1`() = checkErrors("""
        fn main() {
            let _ = <error descr="const closures is experimental [E0658]">const</error> || {};
        }
    """)

    @MockRustcVersion("1.68.0-nightly")
    fun `test const closures E0658 2`() = checkErrors("""
        #![feature(const_closures)]
        fn main() {
            let _ = const || {};
        }
    """)

    fun `test recursive async function E0733`() = checkErrors("""
        async fn func1() {
            func1().<error descr="Recursion in an `async fn` requires boxing [E0733]">await</error>;
            func1();
        }
        async fn func2() {
            func1().await;
        }
        async fn func3() {
            let _ = async || {
                func3().await;
            };
            async fn inner() {
                func3().await;
            }
        }

        #[async_recursion]
        async fn func4() {
            func4().await;
        }
        #[async_recursion::async_recursion]
        async fn func5() {
            func5().await;
        }
    """)

    @MockRustcVersion("1.68.0")
    fun `test error Fn trait manual implementation and Fn trait type parameters is subject to change`() = checkErrors("""
        #[lang = "fn_once"]
        trait FnOnce<Args> {
            type Output;
            fn call_once(self, args: Args) -> Self::Output;
        }

        struct MyStruct {
            foo: i32,
        }

        impl /*error descr="Manual implementations of `FnOnce` are experimental [E0183]"*//*error descr="The precise format of `Fn`-family traits' type parameters is subject to change [E0658]"*/FnOnce<()>/*error**//*error**/ for MyStruct {}
    """)

    @MockRustcVersion("1.68.0")
    fun `test error Fn trait manual implementation - parenthetical notation`() = checkErrors("""
        #[lang = "fn_once"]
        trait FnOnce<Args> {
            type Output;
            fn call_once(self, args: Args) -> Self::Output;
        }

        struct MyStruct {
            foo: i32,
        }

        impl /*error descr="Manual implementations of `FnOnce` are experimental [E0183]"*/FnOnce(())/*error**/ for MyStruct {}
    """)

    @MockRustcVersion("1.68.0-nightly")
    fun `test Fn trait manual implementation`() = checkErrors("""
        #![feature(unboxed_closures)]

        #[lang = "fn_once"]
        trait FnOnce<Args> {
            type Output;
            fn call_once(self, args: Args) -> Self::Output;
        }

        struct MyClosure {
            foo: i32,
        }

        impl FnOnce(()) for MyClosure {}
    """)

    @MockRustcVersion("1.68.0")
    fun `test error parenthetical notation for non Fn trait`() = checkErrors("""
        trait MyTrait {}

        fn foo<T>() where T: /*error descr="Parenthetical notation is only stable when used with `Fn`-family traits [E0658]"*/MyTrait(u8) -> u8/*error**/  {}
    """)

    @MockRustcVersion("1.68.0")
    fun `test error`() = checkErrors("""
        #[lang = "fn_once"]
        trait FnOnce<Args> {
            type Output;
            fn call_once(self, args: Args) -> Self::Output;
        }

        fn foo<T, E>(f: T) where T: FnOnce() -> E {}
    """)

    @MockRustcVersion("1.68.0")
    fun `test replace exclusive range pattern with inclusive`() = checkFixByTextWithoutHighlighting("Replace with `1..=2`", """
        fn foo(x: i32) {
            match x {
                /*caret*/1..2 => println!("a")
            }
        }
    """, """
        fn foo(x: i32) {
            match x {
                /*caret*/1..=2 => println!("a")
            }
        }
    """)

    @MockRustcVersion("1.71.0")
    fun `test c str literal E0658 1`() = checkErrors("""
        fn main() {
            <error descr="`c\"..\"` literals is experimental [E0658]">c"foo"</error>;
            <error descr="`c\"..\"` literals is experimental [E0658]">cr#"foo"#</error>;
        }
    """)

    @MockRustcVersion("1.71.0-nightly")
    fun `test c str literal E0658 2`() = checkErrors("""
        #![feature(c_str_literals)]
        fn main() {
            c"foo";
            cr#"foo"#;
        }
    """)
}
