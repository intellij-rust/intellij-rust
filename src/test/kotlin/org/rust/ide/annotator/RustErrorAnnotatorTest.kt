package org.rust.ide.annotator

class RustErrorAnnotatorTest: RustAnnotatorTestBase() {
    override val dataPath = "org/rust/ide/annotator/fixtures/errors"

    fun testInvalidModuleDeclarations() = doTest("helper.rs")
    fun testInvalidTraitImplFix() = checkQuickFix("Implement methods")

    fun testCreateFileQuickFix() = checkByDirectory {
        openFileInEditor("mod.rs")
        applyQuickFix("Create module file")
    }

    fun testCreateFileAndExpandModuleQuickFix() = checkByDirectory {
        openFileInEditor("foo.rs")
        applyQuickFix("Create module file")
    }

    fun testInvalidTraitImpl() = checkErrors("""
        trait T {
            fn foo() {}
            fn bar();
            fn baz();
        }

        <error descr="Not all trait items implemented, missing: `bar`">impl T for ()</error> {
            fn baz() {}
            fn <error descr="Method is not a member of trait `T`">quux</error>() {}
        }
    """)

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

    fun testE0202_TypeAliasInInherentImpl() = checkErrors("""
        struct Foo;
        impl Foo {
            <error descr="Associated types are not allowed in inherent impls [E0202]">type Long = i64;</error>
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
