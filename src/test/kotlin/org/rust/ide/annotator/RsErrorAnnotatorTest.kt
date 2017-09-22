/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.fileTreeFromText

class RsErrorAnnotatorTest : RsAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/errors"

    fun testInvalidModuleDeclarations() = doTest("helper.rs")

    fun testCreateFileQuickFix() = checkByDirectory {
        openFileInEditor("mod.rs")
        applyQuickFix("Create module file")
    }

    fun testCreateFileAndExpandModuleQuickFix() = checkByDirectory {
        openFileInEditor("foo.rs")
        applyQuickFix("Create module file")
    }

    fun testPaths() = checkErrors("""
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

    fun testInvalidChainComparison() = checkErrors("""
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

    fun testValidChainComparison() = checkErrors("""
        fn foo(x: i32, y: bool) {
            let _ = 1 < x && x < 10;
            let _ = 1 < x || x < 10;
            let _ = (1 == x) == y;
            let _ = y == (1 == x);
        }
    """)

    fun testE0046_AbsentMethodInTraitImpl() = checkErrors("""
        trait TError {
            fn bar();
            fn baz();
            fn boo();
        }
        <error descr="Not all trait items implemented, missing: `bar`, `boo` [E0046]">impl TError for ()</error> {
            fn baz() {}
        }
    """)

    fun testE0046_NotApplied() = checkErrors("""
        trait T {
            fn foo() {}
            fn bar();
        }
        impl T for() {
            fn bar() {}
        }
    """)

    fun testE0046_IgnoreMacros() = checkErrors("""
        trait T { fn foo(&self); }

        macro_rules! impl_foo {
            () => { fn foo(&self) {} };
        }

        struct S;

        impl T for S { impl_foo!(); }
    """)

    fun testE0050_IncorrectParamsNumberInTraitImpl() = checkErrors("""
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

    fun testE0060_InvalidParametersNumberInVariadicFunctions() = checkErrors("""
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

    fun testE0061_InvalidParametersNumberInFreeFunctions() = checkErrors("""
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

    fun testE0061_InvalidParametersNumberInAssocFunction() = checkErrors("""
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

    fun testE0061_InvalidParametersNumberInImplMethods() = checkErrors("""
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

    fun testE0061_InvalidParametersNumberInTupleStructs() = checkErrors("""
        struct Foo0();
        struct Foo1(u8);
        fn main() {
            let _ = Foo0();
            let _ = Foo1(1);

            let _ = Foo0<error descr="This function takes 0 parameters but 1 parameter was supplied [E0061]">(4)</error>;
            let _ = Foo1<error descr="This function takes 1 parameter but 2 parameters were supplied [E0061]">(10, false)</error>;
        }
    """)

    fun testE0061_InvalidParametersNumberInTupleEnumVariants() = checkErrors("""
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

    fun testE0061_RespectsCfgAttribute() = checkErrors("""
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
    fun testE0061_IgnoresTraitImplementations() = checkErrors("""
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

    fun `test E0069 empty return`() = checkErrors("""
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

    fun `test E0121 type placeholder in signatures`() = checkErrors("""
        fn ok(_: &'static str) {
            let four = |x: _| 4;
            let _ = match (8, 3) { (_, _) => four(1) };
            if let Some(_) = Some(0) {}
        }

        fn foo(a: <error descr="The type placeholder `_` is not allowed within types on item signatures [E0121]">_</error>) {}
        fn bar() -> <error>_</error> {}
        fn baz(t: (u32, <error>_</error>)) -> (bool, (f64, <error>_</error>)) {}
        static FOO: <error>_</error> = 42;
    """)

    fun testE0124_NameDuplicationInStruct() = checkErrors("""
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

    fun testE0185_SelfInImplNotInTrait() = checkErrors("""
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

    fun testE0186_SelfInTraitNotInImpl() = checkErrors("""
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

    fun `testE0198 Negative impls for traits`() = checkErrors("""
        struct Foo;
        struct Foo2;

        trait Bar1 { }
        unsafe trait Bar2 { }

        impl !Bar1 for Foo { }
        impl !Bar2 for Foo { }
        unsafe impl !<error descr="Negative implementations are not unsafe [E0198]">Bar1</error> for Foo2 { }
        unsafe impl !<error descr="Negative implementations are not unsafe [E0198]">Bar2</error> for Foo2 { }
    """)

    fun `testE0199 Only safe impls for safe traits`() = checkErrors("""
        struct Foo;
        struct Foo2;

        trait Bar { }

        unsafe impl <error descr="Implementing the trait `Bar` is not unsafe [E0199]">Bar</error> for Foo { }
        impl Bar for Foo2 { }
    """)

    fun `testE0200 Only unsafe impls for unsafe traits`() = checkErrors("""
        struct Foo;
        struct Foo2;

        unsafe trait Bar { }

        unsafe impl Bar for Foo { }
        impl <error descr="The trait `Bar` requires an `unsafe impl` declaration [E0200]">Bar</error> for Foo2 { }
    """)

    fun testE0201_NameDuplicationInImpl() = checkErrors("""
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

    fun `test E0261 undeclared lifetimes`() = checkErrors("""
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

    fun `test E0261 not applied to static lifetimes`() = checkErrors("""
        const ZERO: &'static u32 = &0;
        fn foo(a: &'static str) {}
    """)

    fun testE0263_LifetimeNameDuplicationInGenericParams() = checkErrors("""
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

    fun testE0403_NameDuplicationInGenericParams() = checkErrors("""
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

    fun testE0407_UnknownMethodInTraitImpl() = checkErrors("""
        trait T {
            fn foo();
        }
        impl T for () {
            fn foo() {}
            fn <error descr="Method `quux` is not a member of trait `T` [E0407]">quux</error>() {}
        }
    """)

    fun testE0415_NameDuplicationInParamList() = checkErrors("""
        fn foo(x: u32, X: u32) {}
        fn bar<T>(T: T) {}

        fn simple(<error descr="Identifier `a` is bound more than once in this parameter list [E0415]">a</error>: u32,
                  b: bool,
                  <error>a</error>: f64) {}
        fn tuples(<error>a</error>: u8, (b, (<error>a</error>, c)): (u16, (u32, u64))) {}
    """)

    fun `test E0426 undeclared label`() = checkErrors("""
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

    fun testE0428_NameDuplicationInCodeBlock() = checkErrors("""
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

    fun testE0428_NameDuplicationInEnum() = checkErrors("""
        enum Directions {
            NORTH,
            <error descr="Enum variant `SOUTH` is already declared [E0428]">SOUTH</error> { distance: f64 },
            WEST,
            <error descr="Enum variant `SOUTH` is already declared [E0428]">SOUTH</error> { distance: f64 },
            EAST
        }
    """)

    fun testE0428_NameDuplicationInForeignMod() = checkErrors("""
        extern "C" {
            static mut UNIQUE: u16;
            fn unique();

            static mut <error descr="A value named `DUP` has already been defined in this module [E0428]">DUP</error>: u32;
            static mut <error descr="A value named `DUP` has already been defined in this module [E0428]">DUP</error>: u32;

            fn <error descr="A value named `dup` has already been defined in this module [E0428]">dup</error>();
            fn <error descr="A value named `dup` has already been defined in this module [E0428]">dup</error>();
        }
    """)

    fun testE0428_NameDuplicationInFile() = checkErrors("""
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

    fun testE0428_NameDuplicationInModule() = checkErrors("""
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

    fun testE0428_NameDuplicationInTrait() = checkErrors("""
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

    fun testE0428_RespectsNamespaces() = checkErrors("""
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

    fun testE0428_IgnoresLocalBindings() = checkErrors("""
        mod no_dup {
            fn no_dup() {
                let no_dup: bool = false;
                fn no_dup(no_dup: u23) {
                    mod no_dup {}
                }
            }
        }
    """)

    fun testE0428_IgnoresInnerContainers() = checkErrors("""
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

    fun testE0428_RespectsCfgAttribute() = checkErrors("""
        mod opt {
            #[cfg(not(windows))] mod foo {}
            #[cfg(windows)]     mod foo {}

            #[cfg(windows)] fn <error descr="A value named `hello_world` has already been defined in this module [E0428]">hello_world</error>() {}
            fn <error descr="A value named `hello_world` has already been defined in this module [E0428]">hello_world</error>() {}
        }
    """)

    fun testE0449_UnnecessaryPub() = checkErrors("""
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

    fun `testE0424 ignore non static`() = checkErrors("""
        struct Foo;

        impl Foo {
            fn foo(self) {
                let a = self;
            }
        }
    """)

    fun `testE0424 ignore module path`() = checkErrors("""
        fn foo() {
        }

        fn bar() {
            self::foo()
        }
    """)

    fun `test don't touch AST in other files`() = checkDontTouchAstInOtherFiles(
        fileTreeFromText("""
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
    )

    fun `test a private item was used outside of its scope E0624`() = checkErrors("""
        mod some_module {
            pub struct Foo;

            impl Foo {
                fn method(&self) {}
            }
        }
        fn main() {
            let f = some_module::Foo;
            f.<error>method</error>();
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
            f.<error>x</error>;// error: field `x` of struct `some_module::Foo` is private
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

    fun `test should not annotate super mod E0624`() = checkDontTouchAstInOtherFiles(
        fileTreeFromText("""
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
        """), filePath = "m/mod.rs"
    )

    fun `test const outside scope E0603`()  = checkErrors("""
        mod foo {
            const BAR: u32 = 0x_a_bad_1dea_u32;
        }

        use <error>foo::BAR</error>;
    """)

    fun `test not const outside scope E0603`()  = checkErrors("""
        mod foo {
            pub const BAR: u32 = 0x_a_bad_1dea_u32;
        }

        use foo::BAR;
    """)

    fun `test fn outside scope E0603`()  = checkErrors("""
        mod foo {
            fn bar() {}
        }

        use <error>foo::bar</error>;
    """)

    fun `test not fn outside scope E0603`()  = checkErrors("""
        mod foo {
            pub fn bar() {}
        }

        use foo::bar;
    """)

    fun `test struct outside scope E0603`()  = checkErrors("""
        mod foo {
            struct Bar;
        }

        use <error>foo::Bar</error>;
    """)

    fun `test struct fn outside scope E0603`()  = checkErrors("""
        mod foo {
            pub struct Bar;
        }

        use foo::Bar;
    """)

    fun `test type mismatch E0308 primitive`() = checkErrors("""
        fn main () {
            let _: u8 = <error>1u16</error>;
        }
    """)

    fun `test type mismatch E0308 struct`() = checkErrors("""
        struct X; struct Y;
        fn main () {
            let _: X = <error>Y</error>;
        }
    """)

    fun `test type mismatch E0308 tuple`() = checkErrors("""
        fn main () {
            let _: (u8, ) = (<error>1u16</error>, );
        }
    """)

    // TODO error should be more local
    fun `test type mismatch E0308 array`() = checkErrors("""
        fn main () {
            let _: [u8; 1] = <error>[1u16]</error>;
        }
    """)

    fun `test type mismatch E0308 array size`() = checkErrors("""
        fn main () {
            let _: [u8; 1] = <error>[1, 2]</error>;
        }
    """)

    fun `test type mismatch E0308 struct field`() = checkErrors("""
        struct S { f: u8 }
        fn main () {
            S { f: <error>1u16</error> };
        }
    """)

    fun `test type mismatch E0308 function parameter`() = checkErrors("""
        fn foo(_: u8) {}
        fn main () {
            foo(<error>1u16</error>)
        }
    """)

    fun `test type mismatch E0308 unconstrained integer`() = checkErrors("""
        struct S;
        fn main () {
            let mut a = 0;
            a = <error>S</error>;
        }
    """)

}
