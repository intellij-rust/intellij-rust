/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

/**
 * Tests for the set of Naming Convention inspections.
 */
abstract class RsNamingInspectionTest(inspection: RsNamingInspection) : RsInspectionsTestBase(inspection) {
    class RsAssocTypeNamingInspectionTest : RsNamingInspectionTest(RsAssocTypeNamingInspection()) {
        fun `test associated types`() = checkByText("""
            trait Foo {
                type AssocTypeOk;
                type <warning descr="Type `assoc_foo` should have a camel case name such as `AssocFoo`">assoc_foo</warning>;
            }
        """)

        fun `test associated types suppression`() = checkByText("""
            #[allow(non_camel_case_types)]
            trait Foo {
                type assoc_foo;
            }
        """)

        // TODO: Uncomment when associated types support renaming
        //
        // fun `test associated types fix`() = checkFixByText("Rename to `AssocFoo`", """
        //     trait Foo {
        //         type <warning descr="Type `assoc_foo` should have a camel case name such as `AssocFoo`">ass<caret>oc_foo</warning>;
        //         fn bar(foo: &Self::assoc_foo) {}
        //     }
        // """, """
        //     trait Foo {
        //         type AssocFoo;
        //         fn bar(foo: &Self::AssocFoo) {}
        //     }
        // """)
    }

    class RsConstNamingInspectionTest : RsNamingInspectionTest(RsConstNamingInspection()) {
        fun `test constants`() = checkByText("""
            const CONST_OK: u32 = 12;
            const <warning descr="Constant `const_foo` should have an upper case name such as `CONST_FOO`">const_foo</warning>: u32 = 12;
        """)

        fun `test constants suppression`() = checkByText("""
            #[allow(non_upper_case_globals)]
            const const_foo: u32 = 12;
        """)

        fun `test constants fix`() = checkFixByText("Rename to `CONST_FOO`", """
            const <warning descr="Constant `ConstFoo` should have an upper case name such as `CONST_FOO`">Con<caret>stFoo</warning>: u32 = 42;
            fn const_use() {
                let a = ConstFoo;
            }
        """, """
            const CONST_FOO: u32 = 42;
            fn const_use() {
                let a = CONST_FOO;
            }
        """)
    }

    class RsEnumNamingInspectionTest: RsNamingInspectionTest(RsEnumNamingInspection()) {
        fun `test enums`() = checkByText("""
            enum EnumOk {}
            enum <warning descr="Type `enum_foo` should have a camel case name such as `EnumFoo`">enum_foo</warning> {}
        """)

        fun `test enums suppression`() = checkByText("""
            #[allow(non_camel_case_types)]
            enum enum_foo {}
        """)

        fun `test enums fix`() = checkFixByText("Rename to `EnumFoo`", """
            enum <warning descr="Type `enum_foo` should have a camel case name such as `EnumFoo`">enum_f<caret>oo</warning> { Var }
            fn enum_use() {
                let a = enum_foo::Var;
            }
        """, """
            enum EnumFoo { Var }
            fn enum_use() {
                let a = EnumFoo::Var;
            }
        """)
    }

    class RsEnumVariantNamingInspectionTest: RsNamingInspectionTest(RsEnumVariantNamingInspection()) {
        fun `test enum variants`() = checkByText("""
            enum EnumVars {
                VariantOk,
                <warning descr="Enum variant `variant_foo` should have a camel case name such as `VariantFoo`">variant_foo</warning>
            }
        """)

        fun `test enum variants suppression`() = checkByText("""
            #[allow(non_camel_case_types)]
            enum EnumVars {
                variant_foo
            }
        """)

        fun `test enum variants fix`() = checkFixByText("Rename to `VarBar`", """
            enum ToFix {
                <warning descr="Enum variant `var_bar` should have a camel case name such as `VarBar`">var_b<caret>ar</warning>
            }
            fn enum_var_use() {
                let a = ToFix::var_bar;
            }
        """, """
            enum ToFix {
                VarBar
            }
            fn enum_var_use() {
                let a = ToFix::VarBar;
            }
        """)
    }

    class RsFieldNamingInspectionTest: RsNamingInspectionTest(RsFieldNamingInspection()) {
        fun `test enum variant fields`() = checkByText("""
            enum EnumVarFields {
                Variant {
                    field_ok: u32,
                    <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">FieldFoo</warning>: u32
                }
            }
        """)

        fun `test enum variant fields suppression`() = checkByText("""
            #[allow(non_snake_case)]
            enum EnumVarFields {
                Variant {
                    FieldFoo: u32
                }
            }
        """)

        fun `test enum variant fields fix`() = checkFixByText("Rename to `field_foo`", """
            enum EnumToFix {
                Test {
                    <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">Fi<caret>eldFoo</warning>: u32
                }
            }
            fn enum_use() {
                let mut a = EnumToFix::Test{ FieldFoo: 12 };
            }
        """, """
            enum EnumToFix {
                Test {
                    field_foo: u32
                }
            }
            fn enum_use() {
                let mut a = EnumToFix::Test{ field_foo: 12 };
            }
        """)

        fun `test struct fields`() = checkByText("""
            struct Foo {
                field_ok: u32,
                <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">FieldFoo</warning>: u32
            }
        """)

        fun `test struct fields suppression`() = checkByText("""
            #[allow(non_snake_case)]
            pub struct HoverParams {
                pub textDocument: Document,
                pub position: Position
            }
        """)

        fun `test struct fields fix`() = checkFixByText("Rename to `is_deleted`", """
            struct Foo {
                <warning descr="Field `IsDeleted` should have a snake case name such as `is_deleted`">IsDelete<caret>d</warning>: bool
            }
            fn struct_use() {
                let a = Foo { IsDeleted: false };
            }
        """, """
            struct Foo {
                is_deleted: bool
            }
            fn struct_use() {
                let a = Foo { is_deleted: false };
            }
        """)

    }

    class RsFunctionNamingInspectionTest: RsNamingInspectionTest(RsFunctionNamingInspection()) {
        fun `test functions`() = checkByText("""
            fn fn_ok() {}
            fn <warning descr="Function `FN_BAR` should have a snake case name such as `fn_bar`">FN_BAR</warning>() {}

            extern "C" {
                fn OK();
            }
        """)

        fun `test functions suppression`() = checkByText("""
            #[allow(non_snake_case)]
            fn FN_BAR() {}
        """)

        fun `test functions fix`() = checkFixByText("Rename to `fun_foo`", """
            fn <warning descr="Function `FUN_FOO` should have a snake case name such as `fun_foo`">F<caret>UN_FOO</warning>() {}
            fn fun_use() {
                FUN_FOO();
            }
        """, """
            fn fun_foo() {}
            fn fun_use() {
                fun_foo();
            }
        """)
    }

    class RsArgumentNamingInspectionTest: RsNamingInspectionTest(RsArgumentNamingInspection()) {
        fun `test function arguments`() = checkByText("""
            fn fn_par(
                par_ok: u32,
                <warning descr="Argument `ParFoo` should have a snake case name such as `par_foo`">ParFoo</warning>: u32) {
            }
        """)

        fun `test function arguments suppression`() = checkByText("""
            #[allow(non_snake_case)]
            fn fn_par(ParFoo: u32) {}
        """)

        fun `test function arguments fix`() = checkFixByText("Rename to `arg_baz`", """
            fn test (<warning descr="Argument `Arg__Baz_` should have a snake case name such as `arg_baz`">Arg__<caret>Baz_</warning>: u32) {
                println!("{}", Arg__Baz_);
            }
        """, """
            fn test (arg_baz: u32) {
                println!("{}", arg_baz);
            }
        """)

        fun `test method arguments`() = checkByText("""
            struct Foo {}
            impl Foo {
                fn fn_par(
                    par_ok: u32,
                    <warning descr="Argument `ParFoo` should have a snake case name such as `par_foo`">ParFoo</warning>: u32,) {
                }
            }
        """)

        fun `test method arguments suppression`() = checkByText("""
            #![allow(non_snake_case)]
            struct Foo {}
            impl Foo {
                fn fn_par(ParFoo: u32,) {}
            }
        """)

        fun `test method arguments fix`() = checkFixByText("Rename to `m_arg`", """
            struct Foo;
            impl Foo {
                fn print(&self, <warning descr="Argument `mArg` should have a snake case name such as `m_arg`">m<caret>Arg</warning>: u32) {
                    println!("{}", mArg);
                }
            }
        """, """
            struct Foo;
            impl Foo {
                fn print(&self, m_arg: u32) {
                    println!("{}", m_arg);
                }
            }
        """)
    }

    class RsLifetimeNamingInspectionTest: RsNamingInspectionTest(RsLifetimeNamingInspection()) {
        fun `test lifetimes`() = checkByText("""
            fn lifetimes<
                'lifetime_ok,
                '__,
                <warning descr="Lifetime `'LifetimeFoo` should have a snake case name such as `'lifetime_foo`">'LifetimeFoo</warning>>() {
            }
        """)

        fun `test lifetimes suppression`() = checkByText("""
            #[allow(non_snake_case)]
            fn lifetimes<'LifetimeFoo>() {}
        """)

        fun `test lifetimes fix`() = checkFixByText("Rename to `'lifetime_foo`", """
            fn lifetimes<
                <warning descr="Lifetime `'LifetimeFoo` should have a snake case name such as `'lifetime_foo`">'Lifetime<caret>Foo</warning>>(x: &'LifetimeFoo u32) {
            }
        """, """
            fn lifetimes<
                'lifetime_foo>(x: &'lifetime_foo u32) {
            }
        """)
    }

    class RsMacroNamingInspectionTest: RsNamingInspectionTest(RsMacroNamingInspection()) {
        fun `test macros`() = checkByText("""
            macro_rules! macro_ok { () => {}; }
            macro_rules! <warning descr="Macro `MacroFoo` should have a snake case name such as `macro_foo`">MacroFoo</warning> { () => {}; }
        """)

        fun `test macros suppression`() = checkByText("""
            #[allow(non_snake_case)]
            macro_rules! MacroFoo { () => {}; }
        """)

        fun `test macros fix`() = checkFixByText("Rename to `macro_foo`", """
            macro_rules! <warning descr="Macro `MacroFoo` should have a snake case name such as `macro_foo`">Macro<caret>Foo</warning> { () => {}; }
            MacroFoo!();
        """, """
            macro_rules! macro_foo { () => {}; }
            macro_foo!();
        """)
    }

    class RsMethodNamingInspectionTest: RsNamingInspectionTest(RsMethodNamingInspection()) {
        fun `test methods`() = checkByText("""
            struct Foo {}
            impl Foo {
                fn met_ok(&self) {}
                fn <warning descr="Method `MET_BAR` should have a snake case name such as `met_bar`">MET_BAR</warning>(&self) {}
            }
        """)

        fun `test methods suppression`() = checkByText("""
            #![allow(non_snake_case)]
            struct Foo {}
            impl Foo {
                fn MET_BAR(&self) {}
            }
        """)

        fun `test methods fix`() = checkFixByText("Rename to `met_bar`", """
            struct Foo;
            impl Foo {
                fn <warning descr="Method `MetBar` should have a snake case name such as `met_bar`">MetB<caret>ar</warning>(&self) {}
            }
            fn method_use() {
                let f = Foo {};
                f.MetBar();
            }
        """, """
            struct Foo;
            impl Foo {
                fn met_bar(&self) {}
            }
            fn method_use() {
                let f = Foo {};
                f.met_bar();
            }
        """)

        fun `test trait methods`() = checkByText("""
            trait Foo {
                fn met_ok() {}
                fn <warning descr="Method `MET_BAR` should have a snake case name such as `met_bar`">MET_BAR</warning>() {}
            }
        """)

        fun `test trait methods suppression`() = checkByText("""
            trait Foo {
                #[allow(non_snake_case)]
                fn MET_BAR() {}
            }
        """)

        // TODO: Uncomment when trait methods support renaming
        //
        // fun `test trait methods fix`() = checkFixByText("Rename to `bar_baz`", """
        //     trait Foo {
        //         fn <warning descr="Method `BarBaz` should have a snake case name such as `bar_baz`">Bar<caret>Baz</warning>() {}
        //     }
        //     struct Food;
        //     impl Foo for Food {
        //         fn BarBaz() {}
        //     }
        // """, """
        //     trait Foo {
        //         fn bar_baz() {}
        //     }
        //     struct Food;
        //     impl Foo for Food {
        //         fn bar_baz() {}
        //     }
        // """)
    }

    class RsModuleNamingInspectionTest: RsNamingInspectionTest(RsModuleNamingInspection()) {
        fun `test modules`() = checkByText("""
            mod module_ok {}
            mod <warning descr="Module `moduleA` should have a snake case name such as `module_a`">moduleA</warning> {}
        """)

        fun `test modules suppression`() = checkByText("""
            #[allow(non_snake_case)]
            mod moduleA {}
        """)

        fun `test modules suppression bad style`() = checkByText("""
            #[allow(bad_style)]
            mod moduleA {}
        """)

        fun `test modules fix`() = checkFixByText("Rename to `mod_foo`", """
            mod <warning descr="Module `modFoo` should have a snake case name such as `mod_foo`">modF<caret>oo</warning> {
                pub const ONE: u32 = 1;
            }
            fn use_mod() {
                let a = modFoo::ONE;
            }
        """, """
            mod mod_foo {
                pub const ONE: u32 = 1;
            }
            fn use_mod() {
                let a = mod_foo::ONE;
            }
        """)
    }

    class RsStaticConstNamingInspectionTest: RsNamingInspectionTest(RsStaticConstNamingInspection()) {
        fun `test statics`() = checkByText("""
            static STATIC_OK: u32 = 12;
            static <warning descr="Static constant `static_foo` should have an upper case name such as `STATIC_FOO`">static_foo</warning>: u32 = 12;
        """)

        fun `test statics suppression`() = checkByText("""
            #[allow(non_upper_case_globals)]
            static static_foo: u32 = 12;
        """)

        fun `test statics fix`() = checkFixByText("Rename to `STATIC_FOO`", """
            static <warning descr="Static constant `staticFoo` should have an upper case name such as `STATIC_FOO`">sta<caret>ticFoo</warning>: u32 = 43;
            fn static_use() {
                let a = staticFoo;
            }
        """, """
            static STATIC_FOO: u32 = 43;
            fn static_use() {
                let a = STATIC_FOO;
            }
        """)
    }

    class RsStructNamingInspectionTest: RsNamingInspectionTest(RsStructNamingInspection()) {
        fun `test structs`() = checkByText("""
            struct StructOk {}
            struct <warning descr="Type `struct_foo` should have a camel case name such as `StructFoo`">struct_foo</warning> {}
        """)

        fun `test structs suppression`() = checkByText("""
            #[allow(non_camel_case_types)]
            struct struct_foo {}
        """)

        fun `test structs fix`() = checkFixByText("Rename to `StructFoo`", """
            struct <warning descr="Type `Struct_foo` should have a camel case name such as `StructFoo`">Stru<caret>ct_foo</warning> {}
            fn struct_use() {
                let a = Struct_foo {};
            }
        """, """
            struct StructFoo {}
            fn struct_use() {
                let a = StructFoo {};
            }
        """)
    }

    class RsTraitNamingInspectionTest: RsNamingInspectionTest(RsTraitNamingInspection()) {
        fun `test traits`() = checkByText("""
            trait TraitOk {}
            trait <warning descr="Trait `trait_foo` should have a camel case name such as `TraitFoo`">trait_foo</warning> {}
        """)

        fun `test traits suppression`() = checkByText("""
            #[allow(non_camel_case_types)]
            trait trait_foo {}
        """)

        fun `test traits fix`() = checkFixByText("Rename to `HotFix`", """
             trait <warning descr="Trait `hot_fix` should have a camel case name such as `HotFix`">ho<caret>t_fix</warning> {}
             struct Patch {}
             impl hot_fix for Patch {}
         """, """
             trait HotFix {}
             struct Patch {}
             impl HotFix for Patch {}
         """)
    }

    class RsTypeAliasNamingInspectionTest: RsNamingInspectionTest(RsTypeAliasNamingInspection()) {
        fun `test type aliases`() = checkByText("""
            type TypeOk = u32;
            type <warning descr="Type `type_foo` should have a camel case name such as `TypeFoo`">type_foo</warning> = u32;
        """)

        fun `test type aliases suppression`() = checkByText("""
            #[allow(non_camel_case_types)]
            type type_foo = u32;
        """)

        fun `test type aliases fix`() = checkFixByText("Rename to `ULong`", """
             type <warning descr="Type `u_long` should have a camel case name such as `ULong`">u_<caret>long</warning> = u64;
             const ZERO: u_long = 0;
         """, """
             type ULong = u64;
             const ZERO: ULong = 0;
         """)
    }

    class RsTypeParameterNamingInspectionTest: RsNamingInspectionTest(RsTypeParameterNamingInspection()) {
        fun `test type parameters`() = checkByText("""
            fn type_params<
                SomeType: Clone,
                <warning descr="Type parameter `some_Type` should have a camel case name such as `SomeType`">some_Type</warning>: Clone> () {
            }
        """)

        fun `test type parameters suppression`() = checkByText("""
            #[allow(non_camel_case_types)]
            fn type_params<some_Type: Clone> () {}
        """)

        fun `test type parameters fix`() = checkFixByText("Rename to `To`", """
            fn type_params<<warning descr="Type parameter `to` should have a camel case name such as `To`">t<caret>o</warning>: Clone> () {}
         """, """
            fn type_params<To: Clone> () {}
         """)

        fun `test type parameters with where`() = checkByText("""
            fn type_params<
                SomeType,
                <warning descr="Type parameter `some_Type` should have a camel case name such as `SomeType`">some_Type</warning>>() where some_Type: Clone {
            }
        """)

        fun `test type parameters with where fix`() = checkFixByText("Rename to `Base`", """
            fn type_params<<warning descr="Type parameter `base` should have a camel case name such as `Base`">b<caret>ase</warning>>(b: &base) where base: Clone {}
         """, """
            fn type_params<Base>(b: &Base) where Base: Clone {}
         """)
    }

    class RsVariableNamingInspectionTest: RsNamingInspectionTest(RsVariableNamingInspection()) {
        fun `test variables`() = checkByText("""
            fn loc_var() {
                let var_ok = 12;
                let <warning descr="Variable `VarFoo` should have a snake case name such as `var_foo`">VarFoo</warning> = 12;
            }
        """)

        fun `test variables suppression`() = checkByText("""
            #![allow(non_snake_case)]
            fn loc_var() {
                let VarFoo = 12;
            }
        """)

        fun `test variables within struct`() = checkByText("""
            struct Foo { fld: u32 }
            fn test() {
                let Foo { fld: <warning descr="Variable `FLD_VAL` should have a snake case name such as `fld_val`">FLD_VAL</warning> } = Foo { fld: 17 };
            }
        """)

        fun `test variables fix`() = checkFixByText("Rename to `dwarfs_count`", """
            fn test() {
                let <warning descr="Variable `DWARFS_COUNT` should have a snake case name such as `dwarfs_count`">DWARF<caret>S_COUNT</warning> = 7;
                let legs_count = DWARFS_COUNT * 2;
            }
         """, """
            fn test() {
                let dwarfs_count = 7;
                let legs_count = dwarfs_count * 2;
            }
         """)

        fun `test tuple variables`() = checkByText("""
            fn loc_var() {
                let (var1_ok, var2_ok) = (17, 83);
                let (<warning descr="Variable `VarFoo` should have a snake case name such as `var_foo`">VarFoo</warning>, var2_ok) = (120, 30);
            }
        """)

        fun `test tuple variables fix`() = checkFixByText("Rename to `real`", """
            fn test() {
                let (<warning descr="Variable `Real` should have a snake case name such as `real`">Re<caret>al</warning>, imaginary) = (7.2, 3.5);
                println!("{} + {}i", Real, imaginary);
            }
         """, """
            fn test() {
                let (real, imaginary) = (7.2, 3.5);
                println!("{} + {}i", real, imaginary);
            }
         """)

        // Issue #730. The inspection must not be applied in the following cases
        @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
        fun `test variables not applied`() = checkByText("""
            fn test_not_applied() {
                match Some(()) {
                    None => ()
                }

                match 1 {
                    Foo => { }
                }

                let seven = Some(7);
                if let Some(Number) = seven {
                }

                let (a, b) = (Some(10), Some(12));
                match (a, b) {
                    (None, Some(x)) => {}
                    _ => {}
                }
            }
        """)
    }
}
