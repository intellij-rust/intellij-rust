package org.rust.ide.annotator

class RustErrorAnnotatorTest: RustAnnotatorTestBase() {
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

    fun testConstFree() = checkErrors("""
        const FOO: u32 = 42;
        pub const PUB_FOO: u32 = 41;
        static S_FOO: bool = true;
        static mut S_MUT_FOO: bool = false;
        pub static S_PUB_BAR: u8 = 0;
        pub static mut S_PUB_MUT_BAR: f16 = 1.12;

        <error descr="Constant `BAR` must have a value">const BAR: u8;</error>
        <error descr="Static constant `DEF_BAR` cannot have the `default` qualifier">default</error> static DEF_BAR: u16 = 9;
    """)

    fun testConstInTrait() = checkErrors("""
        trait Foo {
            const FOO_1: u16 = 10;
            const FOO_2: f64;

            <error descr="Constant `PUB_BAZ` cannot have the `pub` qualifier">pub</error> const PUB_BAZ: bool;
            <error descr="Constant `DEF_BAR` cannot have the `default` qualifier">default</error> const DEF_BAR: u16 = 9;
            <error descr="Static constants are not allowed in traits">static</error> ST_FOO: u32 = 18;
        }
    """)

    fun testConstInImpl() = checkErrors("""
        struct Foo;
        impl Foo {
            const FOO: u32 = 109;
            pub const PUB_FOO: u32 = 81;
            default const DEF_FOO: u8 = 1;

            <error descr="Constant `BAR` must have a value">const BAR: u8;</error>
            <error descr="Static constants are not allowed in impl blocks">static</error> ST_FOO: u32 = 18;
        }
    """)

    fun testConstInExtern() = checkErrors("""
        extern "C" {
            static mut FOO: u32;
            pub static mut PUB_FOO: u8;

            <error descr="Static constant `DEF_FOO` cannot have the `default` qualifier">default</error> static mut DEF_FOO: bool;
            <error descr="Only static constants are allowed in extern blocks">const</error> CONST_FOO: u32;
            <error descr="Non mutable static constants are not allowed in extern blocks">static NON_MUT_FOO</error>: u32;
            static mut VAL_FOO: u32 <error descr="Static constants in extern blocks cannot have values">= 10</error>;
        }
    """)

    fun testTypeAliasFree() = checkErrors("""
        type Int = i32;
        pub type UInt = u32;
        type Maybe<T> = Option<T>;
        type SizedMaybe<T> where T: Sized = Option<T>;

        <error descr="Type `DefBool` cannot have the `default` qualifier">default</error> type DefBool = bool;
        <error descr="Aliased type must be provided for type `Unknown`">type Unknown;</error>
        type Show<error descr="Type `Show` cannot have type parameter bounds">: Display</error> = u32;
    """)

    fun testTypeAliasInTrait() = checkErrors("""
        trait Computer {
            type Int;
            type Long = i64;
            type Show: Display;

            <error descr="Type `DefSize` cannot have the `default` qualifier">default</error> type DefSize = isize;
            <error descr="Type `PubType` cannot have the `pub` qualifier">pub</error> type PubType;
            type GenType<error descr="Type `GenType` cannot have generic parameters"><T></error> = Option<T>;
            type WhereType <error descr="Type `WhereType` cannot have `where` clause">where T: Sized</error> = f64;
        }
    """)

    fun testTypeAliasInTraitImpl() = checkErrors("""
            trait Vehicle {
                type Engine;
                type Control;
                type Lock;
                type Cage;
                type Insurance;
                type Driver;
            }
            struct NumericVehicle<T> { foo: T }
            impl<T> Vehicle for NumericVehicle<T> {
                type Engine = u32;
                default type Control = isize;
                type Lock<error descr="Type `Lock` cannot have generic parameters"><T></error> = Option<T>;
                type Cage<error descr="Type `Cage` cannot have type parameter bounds">: Sized</error> = f64;
                type Insurance <error descr="Type `Insurance` cannot have `where` clause">where T: Sized</error> = i8;
                <error descr="Aliased type must be provided for type `Driver`">type Driver;</error>
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

    fun testE0046_ImplementMethodsFix() = checkQuickFix("Implement methods")

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

    fun testE0124_NameDuplicationInStruct() = checkErrors("""
        struct S {
            no_dup: bool,
            dup: u32,
            <error descr="Field `dup` is already declared [E0124]">dup</error>: f64
        }

        enum E {
            VAR1 {
                no_dup: bool
            },
            VAR2 {
                no_dup: bool,
                dup: u32,
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

    fun testE0201_NameDuplicationInImpl() = checkErrors("""
        struct Foo;
        impl Foo {
            fn fn_unique() {}
            fn dup() {}
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
            const TRAIT_DUP: u32 = 100;
            const <error descr="Duplicate definitions with name `TRAIT_DUP` [E0201]">TRAIT_DUP</error>: u32 = 101;
            fn unique() {}
            fn trait_dup() {}
            fn <error descr="Duplicate definitions with name `trait_dup` [E0201]">trait_dup</error>() {}
        }
    """)

    fun testE0202_TypeAliasInInherentImpl() = checkErrors("""
        struct Foo;
        impl Foo {
            <error descr="Associated types are not allowed in inherent impls [E0202]">type Long = i64;</error>
        }
    """)

    fun testE0263_LifetimeNameDuplicationInGenericParams() = checkErrors("""
        fn foo<'a, 'b>(x: &'a str, y: &'b str) { }
        struct Str<'a, 'b> { a: &'a u32, b: &'b f64 }
        impl<'a, 'b> Str<'a, 'b> {}
        enum Direction<'a, 'b> { LEFT(&'a str), RIGHT(&'b str) }
        trait Trait<'a, 'b> {}

        fn foo<'a, 'b, <error descr="Lifetime name `'a` declared twice in the same scope [E0263]">'a</error>>(x: &'a str, y: &'b str) { }
        struct Str<'a, 'b, <error>'a</error>> { a: &'a u32, b: &'b f64 }
        impl<'a, 'b, <error>'a</error>> Str<'a, 'b> {}
        enum Direction<'a, 'b, <error>'a</error>> { LEFT(&'a str), RIGHT(&'b str) }
        trait Trait<'a, 'b, <error>'a</error>> {}
    """)

    fun testE0403_NameDuplicationInGenericParams() = checkErrors("""
        fn sub<T, P>() {}
        struct Str<T, P> { t: T, p: P }
        impl<T, P> Str<T, P> {}
        enum Direction<T, P> { LEFT(T), RIGHT(P) }
        trait Trait<T, P> {}

        fn add<T,   <error descr="The name `T` is already used for a type parameter in this type parameter list [E0403]">T</error>, P>() {}
        struct S<T, <error descr="The name `T` is already used for a type parameter in this type parameter list [E0403]">T</error>, P> { t: T, p: P }
        impl<T,     <error descr="The name `T` is already used for a type parameter in this type parameter list [E0403]">T</error>, P> S<T, P> {}
        enum En<T,  <error descr="The name `T` is already used for a type parameter in this type parameter list [E0403]">T</error>, P> { LEFT(T), RIGHT(P) }
        trait Tr<T, <error descr="The name `T` is already used for a type parameter in this type parameter list [E0403]">T</error>, P> { fn foo(t: T) -> P; }
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

    fun testE0428_NameDuplicationInCodeBlock() = checkErrors("""
        fn abc() {
            const UNIQUE_CONST: i32 = 10;
            static UNIQUE_STATIC: f64 = 0.72;
            fn unique_fn() {}
            struct UniqueStruct;
            trait UniqueTrait {}
            enum UniqueEnum {}
            mod unique_mod {}

            const  Dup: u32 = 20;
            static <error descr="A value named `Dup` has already been defined in this block [E0428]">Dup</error>: i64 = -1.3;
            fn     <error descr="A value named `Dup` has already been defined in this block [E0428]">Dup</error>() {}
            struct <error descr="A value named `Dup` has already been defined in this block [E0428]">Dup</error>;
            trait  <error descr="A type named `Dup` has already been defined in this block [E0428]">Dup</error> {}
            enum   <error descr="A type named `Dup` has already been defined in this block [E0428]">Dup</error> {}
            mod    <error descr="A type named `Dup` has already been defined in this block [E0428]">Dup</error> {}
        }
    """)

    fun testE0428_NameDuplicationInEnum() = checkErrors("""
        enum Directions {
            NORTH,
            SOUTH,
            WEST,
            <error descr="A value named `SOUTH` has already been defined in this enum [E0428]">SOUTH</error> { distance: f64 },
            EAST
        }
    """)

    fun testE0428_NameDuplicationInForeignMod() = checkErrors("""
        extern "C" {
            static mut UNIQUE: u16;
            fn unique();

            static mut DUP: u32;
            static mut <error descr="A value named `DUP` has already been defined in this module [E0428]">DUP</error>: u32;

            fn dup();
            fn <error descr="A value named `dup` has already been defined in this module [E0428]">dup</error>();
        }
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

            const Dup: u32 = 20;
            static <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>: i64 = -1.3;
            fn     <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>() {}
            struct <error descr="A value named `Dup` has already been defined in this module [E0428]">Dup</error>;
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

            type DUP_T;
            type <error descr="A type named `DUP_T` has already been defined in this trait [E0428]">DUP_T</error>;

            const DUP_C: bool;
            const <error descr="A value named `DUP_C` has already been defined in this trait [E0428]">DUP_C</error>: u32;

            fn dup();
            fn <error descr="A value named `dup` has already been defined in this trait [E0428]">dup</error>(&self);
        }
    """)

    fun testE0428_RespectsNamespaces() = checkErrors("""
        mod m {
            type T_NO_C_DUP = bool;  // Consts and types are in different namespaces
            const NO_C_DUP: u32 = 10;

            type NO_F_DUP = u8;      // Functions and types are in different namespaces
            fn NO_F_DUP() {}

            const DUP_V: u8 = 1;     // Consts and functions are in the same namespace (values)
            fn <error descr="A value named `DUP_V` has already been defined in this module [E0428]">DUP_V</error>() {}

            enum DUP_T {}            // enums and traits are in the same namespace (types)
            trait <error descr="A type named `DUP_T` has already been defined in this module [E0428]">DUP_T</error> {}
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
            #[cfg(no(windows))] mod foo {}
            #[cfg(windows)]     mod foo {}

            #[cfg(windows)] fn hello_world() {}
            fn <error descr="A value named `hello_world` has already been defined in this module [E0428]">hello_world</error>() {}
        }
    """)

    fun testE0449_UnnecessaryPub() = checkErrors("""
        <error descr="Unnecessary visibility qualifier [E0449]">pub</error> extern "C" { }

        pub struct S;
        <error descr="Unnecessary visibility qualifier [E0449]">pub</error> impl S {}

        pub trait Foo {
            type A;
            fn b();
            const C: u32;
        }
        struct Bar;
        <error descr="Unnecessary visibility qualifier [E0449]">pub</error> impl Foo for Bar {
            <error descr="Unnecessary visibility qualifier [E0449]">pub</error> type A = u32;
            <error descr="Unnecessary visibility qualifier [E0449]">pub</error> fn b() {}
            <error descr="Unnecessary visibility qualifier [E0449]">pub</error> const C: u32 = 10;
        }
    """)

}
