package org.rust.ide.inspections

import com.intellij.testFramework.LightProjectDescriptor

/**
 * Tests for the set of Naming Convention inspections.
 */
class RustNamingInspectionTest : RustInspectionsTestBase() {

    override val dataPath = ""

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor()

    fun testAssociatedTypes() = checkByText<RustAssocTypeNamingInspection>("""
        trait Foo {
            type AssocTypeOk;
            type _AssocTypeOd;
            type <warning descr="Type `__` should have a camel case name such as `CamelCase`">__</warning>;
            type <warning descr="Type `assoc_foo` should have a camel case name such as `AssocFoo`">assoc_foo</warning>;
            type <warning descr="Type `assocBar` should have a camel case name such as `AssocBar`">assocBar</warning>;
            type <warning descr="Type `ASSOC_BAZ` should have a camel case name such as `AssocBaz`">ASSOC_BAZ</warning>;
        }
    """)

    fun testConstants() = checkByText<RustConstNamingInspection>("""
        const CONST_OK: u32 = 12;
        const _CONST_OK: u32 = 12;
        const _0: u32 = 0;
        const <warning descr="Constant `__` should have an upper case name such as `UPPER_CASE`">__</warning>: u32 = 41;
        const <warning descr="Constant `const_foo` should have an upper case name such as `CONST_FOO`">const_foo</warning>: u32 = 12;
        const <warning descr="Constant `ConstBar` should have an upper case name such as `CONST_BAR`">ConstBar</warning>: u32 = 34;
        const <warning descr="Constant `constBaz` should have an upper case name such as `CONST_BAZ`">constBaz</warning>: u32 = 34;
    """)

    fun testEnums() = checkByText<RustEnumNamingInspection>("""
        enum EnumOk {}
        enum _EnumOk {}
        enum _1 {}
        enum <warning descr="Type `__` should have a camel case name such as `CamelCase`">__</warning> {}
        enum <warning descr="Type `enum_foo` should have a camel case name such as `EnumFoo`">enum_foo</warning> {}
        enum <warning descr="Type `enumBar` should have a camel case name such as `EnumBar`">enumBar</warning> {}
        enum <warning descr="Type `ENUM_BAZ` should have a camel case name such as `EnumBaz`">ENUM_BAZ</warning> {}
    """)

    fun testEnumVariants() = checkByText<RustEnumVariantNamingInspection>("""
        enum EnumVars {
            VariantOk,
            _VariantOk,
            _1,
            <warning descr="Enum variant `__` should have a camel case name such as `CamelCase`">__</warning>,
            <warning descr="Enum variant `variant_foo` should have a camel case name such as `VariantFoo`">variant_foo</warning>,
            <warning descr="Enum variant `variantBar` should have a camel case name such as `VariantBar`">variantBar</warning>,
            <warning descr="Enum variant `VARIANT_BAZ` should have a camel case name such as `VariantBaz`">VARIANT_BAZ</warning>
        }
    """)

    fun testEnumVariantFields() = checkByText<RustFieldNamingInspection>("""
        enum EnumVarFields {
            Variant {
                field_ok: u32,
                _field_ok: u32,
                _3: u32,
                <warning descr="Field `__` should have a snake case name such as `snake_case`">__</warning>: u32,
                <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">FieldFoo</warning>: u32,
                <warning descr="Field `fieldBar` should have a snake case name such as `field_bar`">fieldBar</warning>: u32,
                <warning descr="Field `FIELD_BAZ` should have a snake case name such as `field_baz`">FIELD_BAZ</warning>: u32
            }
        }
    """)

    fun testFunctions() = checkByText<RustFunctionNamingInspection>("""
        fn fn_ok() {}
        fn _fn_ok() {}
        fn _6() {}
        fn <warning descr="Function `__` should have a snake case name such as `snake_case`">__</warning>() {}
        fn <warning descr="Function `FN_BAR` should have a snake case name such as `fn_bar`">FN_BAR</warning>() {}
        fn <warning descr="Function `FnFoo` should have a snake case name such as `fn_foo`">FnFoo</warning>() {}
        fn <warning descr="Function `fnBaz` should have a snake case name such as `fn_baz`">fnBaz</warning>() {}
    """)

    fun testFunctionArguments() = checkByText<RustArgumentNamingInspection>("""
        fn fn_par(
            par_ok: u32,
            _par_ok: u32,
            _1: u32,
            <warning descr="Argument `__` should have a snake case name such as `snake_case`">__</warning>: u32,
            <warning descr="Argument `ParFoo` should have a snake case name such as `par_foo`">ParFoo</warning>: u32,
            <warning descr="Argument `parBar` should have a snake case name such as `par_bar`">parBar</warning>: u32,
            <warning descr="Argument `PAR_BAZ` should have a snake case name such as `par_baz`">PAR_BAZ</warning>: u32) {
        }
    """)

    fun testLifetimes() = checkByText<RustLifetimeNamingInspection>("""
        fn lifetimes<
            'lifetime_ok,
            '_lifetime_ok,
            '__,
            '_9,
            <warning descr="Lifetime `'LifetimeFoo` should have a snake case name such as `'lifetime_foo`">'LifetimeFoo</warning>,
            <warning descr="Lifetime `'lifetimeBar` should have a snake case name such as `'lifetime_bar`">'lifetimeBar</warning>,
            <warning descr="Lifetime `'LIFETIME_BAZ` should have a snake case name such as `'lifetime_baz`">'LIFETIME_BAZ</warning>>() {
        }
    """)

    fun testMacros() = checkByText<RustMacroNamingInspection>("""
        macro_rules! macro_ok { ( $( ${'$'}x:expr ),* ) => {}; }
        macro_rules! _macro_ok { ( $( ${'$'}x:expr ),* ) => {}; }
        macro_rules! _9 { ( $( ${'$'}x:expr ),* ) => {}; }
        macro_rules! <warning descr="Macro `__` should have a snake case name such as `snake_case`">__</warning> { ( $( ${'$'}x:expr ),* ) => {}; }
        macro_rules! <warning descr="Macro `MacroFoo` should have a snake case name such as `macro_foo`">MacroFoo</warning> { ( $( ${'$'}x:expr ),* ) => {}; }
        macro_rules! <warning descr="Macro `macroBar` should have a snake case name such as `macro_bar`">macroBar</warning> { ( $( ${'$'}x:expr ),* ) => {}; }
        macro_rules! <warning descr="Macro `MACRO_BAZ` should have a snake case name such as `macro_baz`">MACRO_BAZ</warning> { ( $( ${'$'}x:expr ),* ) => {}; }
    """)

    fun testMethods() = checkByText<RustMethodNamingInspection>("""
        struct Foo {}
        impl Foo {
            fn met_ok(&self) {}
            fn _met_ok(&self) {}
            fn _5(&self) {}
            fn <warning descr="Method `__` should have a snake case name such as `snake_case`">__</warning>(&self) {}
            fn <warning descr="Method `MET_BAR` should have a snake case name such as `met_bar`">MET_BAR</warning>(&self) {}
            fn <warning descr="Method `MetFoo` should have a snake case name such as `met_foo`">MetFoo</warning>(&self) {}
            fn <warning descr="Method `metBaz` should have a snake case name such as `met_baz`">metBaz</warning>(&self) {}
        }
    """)

    fun testMethodArguments() = checkByText<RustArgumentNamingInspection>("""
        struct Foo {}
        impl Foo {
            fn fn_par(
                par_ok: u32,
                _par_ok: u32,
                _1: u32,
                <warning descr="Argument `__` should have a snake case name such as `snake_case`">__</warning>: u32,
                <warning descr="Argument `ParFoo` should have a snake case name such as `par_foo`">ParFoo</warning>: u32,
                <warning descr="Argument `parBar` should have a snake case name such as `par_bar`">parBar</warning>: u32,
                <warning descr="Argument `PAR_BAZ` should have a snake case name such as `par_baz`">PAR_BAZ</warning>: u32) {
            }
        }
    """)

    fun testTraitlMethods() = checkByText<RustMethodNamingInspection>("""
        trait Foo {
            fn met_ok() {}
            fn _met_ok() {}
            fn _5() {}
            fn <warning descr="Method `__` should have a snake case name such as `snake_case`">__</warning>() {}
            fn <warning descr="Method `MET_BAR` should have a snake case name such as `met_bar`">MET_BAR</warning>() {}
            fn <warning descr="Method `MetFoo` should have a snake case name such as `met_foo`">MetFoo</warning>() {}
            fn <warning descr="Method `metBaz` should have a snake case name such as `met_baz`">metBaz</warning>() {}
        }
    """)

    fun testModules() = checkByText<RustModuleNamingInspection>("""
        mod module_ok {}
        mod _module_ok {}
        mod _7 {}
        mod <warning descr="Module `__` should have a snake case name such as `snake_case`">__</warning> {}
        mod <warning descr="Module `Module1` should have a snake case name such as `module1`">Module1</warning> {}
        mod <warning descr="Module `moduleA` should have a snake case name such as `module_a`">moduleA</warning> {}
        mod <warning descr="Module `MODULE_BAZ` should have a snake case name such as `module_baz`">MODULE_BAZ</warning> {}
    """)

    fun testStatics() = checkByText<RustStaticConstNamingInspection>("""
        static STATIC_OK: u32 = 12;
        static _STATIC_OK: u32 = 12;
        static _6: u32 = 6;
        static <warning descr="Static constant `__` should have an upper case name such as `UPPER_CASE`">__</warning>: u32 = 14;
        static <warning descr="Static constant `static_foo` should have an upper case name such as `STATIC_FOO`">static_foo</warning>: u32 = 12;
        static <warning descr="Static constant `StaticBar` should have an upper case name such as `STATIC_BAR`">StaticBar</warning>: u32 = 34;
        static <warning descr="Static constant `staticBaz` should have an upper case name such as `STATIC_BAZ`">staticBaz</warning>: u32 = 34;
    """)

    fun testStructs() = checkByText<RustStructNamingInspection>("""
        struct StructOk {}
        struct _StructOk {}
        struct _8 {}
        struct <warning descr="Type `__` should have a camel case name such as `CamelCase`">__</warning> {}
        struct <warning descr="Type `struct_foo` should have a camel case name such as `StructFoo`">struct_foo</warning> {}
        struct <warning descr="Type `structBar` should have a camel case name such as `StructBar`">structBar</warning> {}
        struct <warning descr="Type `STRUCT_BAZ` should have a camel case name such as `StructBaz`">STRUCT_BAZ</warning> {}
    """)

    fun testStructFields() = checkByText<RustFieldNamingInspection>("""
        struct Foo {
            field_ok: u32,
            _field_ok: u32,
            _1: u32,
            <warning descr="Field `__` should have a snake case name such as `snake_case`">__</warning>: u32,
            <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">FieldFoo</warning>: u32,
            <warning descr="Field `fieldBar` should have a snake case name such as `field_bar`">fieldBar</warning>: u32,
            <warning descr="Field `FIELD_BAZ` should have a snake case name such as `field_baz`">FIELD_BAZ</warning>: u32
        }
    """)

    fun testTraits() = checkByText<RustTraitNamingInspection>("""
        trait TraitOk {}
        trait _TraitOk {}
        trait _4 {}
        trait <warning descr="Trait `__` should have a camel case name such as `CamelCase`">__</warning> {}
        trait <warning descr="Trait `trait_foo` should have a camel case name such as `TraitFoo`">trait_foo</warning> {}
        trait <warning descr="Trait `traitFoo` should have a camel case name such as `TraitFoo`">traitFoo</warning> {}
        trait <warning descr="Trait `TRAIT_BAZ` should have a camel case name such as `TraitBaz`">TRAIT_BAZ</warning> {}
    """)

    fun testTypeAliases() = checkByText<RustTypeAliasNamingInspection>("""
        type TypeOk = u32;
        type _TypeOk = u32;
        type _32 = u32;
        type <warning descr="Type `__` should have a camel case name such as `CamelCase`">__</warning> = u32;
        type <warning descr="Type `type_foo` should have a camel case name such as `TypeFoo`">type_foo</warning> = u32;
        type <warning descr="Type `typeBar` should have a camel case name such as `TypeBar`">typeBar</warning> = u32;
        type <warning descr="Type `TYPE_BAZ` should have a camel case name such as `TypeBaz`">TYPE_BAZ</warning> = u32;
    """)

    fun testTypeParameters() = checkByText<RustTypeParameterNamingInspection>("""
        fn type_params<
            SomeType: Clone,
            _SomeType: Clone,
            _8: Clone,
            <warning descr="Type parameter `__` should have a camel case name such as `CamelCase`">__</warning>: Clone,
            <warning descr="Type parameter `some_Type` should have a camel case name such as `SomeType`">some_Type</warning>: Clone,
            <warning descr="Type parameter `someType` should have a camel case name such as `SomeType`">someType</warning>: Clone,
            <warning descr="Type parameter `SOMET_TYPE` should have a camel case name such as `SometType`">SOMET_TYPE</warning>: Clone>() {
        }
    """)

    fun testTypeParametersWithWhere() = checkByText<RustTypeParameterNamingInspection>("""
        fn type_params<
            SomeType,
            _SomeType,
            _1,
            <warning descr="Type parameter `__` should have a camel case name such as `CamelCase`">__</warning>,
            <warning descr="Type parameter `some_Type` should have a camel case name such as `SomeType`">some_Type</warning>,
            <warning descr="Type parameter `someType` should have a camel case name such as `SomeType`">someType</warning>,
            <warning descr="Type parameter `SOMET_TYPE` should have a camel case name such as `SometType`">SOMET_TYPE</warning>>() where SomeType: Clone, _SomeType: Clone, some_Type: Clone, someType: Clone, SOMET_TYPE: Clone {
        }
    """)

    fun testVariables() = checkByText<RustVariableNamingInspection>("""
        fn loc_var() {
            let var_ok = 12;
            let _var_ok = 12;
            let _7 = 7;
            let <warning descr="Variable `__` should have a snake case name such as `snake_case`">__</warning> = 12;
            let <warning descr="Variable `VarFoo` should have a snake case name such as `var_foo`">VarFoo</warning> = 12;
            let <warning descr="Variable `varBar` should have a snake case name such as `var_bar`">varBar</warning> = 12;
            let <warning descr="Variable `VAR_BAZ` should have a snake case name such as `var_baz`">VAR_BAZ</warning> = 12;
        }
    """)

    fun testSnakeCase() = checkByText<RustFunctionNamingInspection>("""
        fn snake_case() {}
        fn __snake_case__() {}
        fn _12345() {}
        fn <warning descr="Function `___` should have a snake case name such as `snake_case`">___</warning>() {}
        fn <warning descr="Function `___FOoBarBAZ___boo___` should have a snake case name such as `___foo_bar_baz_boo`">___FOoBarBAZ___boo___</warning>() {}
        fn <warning descr="Function `___123___45A___` should have a snake case name such as `___123_45a`">___123___45A___</warning>() {}
    """)

    fun testUpperCase() = checkByText<RustConstNamingInspection>("""
        const UPPER_CASE: u32 = 1;
        const __UPPER_CASE__: u32 = 1;
        const _12345: u32 = 12345;
        const <warning descr="Constant `___` should have an upper case name such as `UPPER_CASE`">___</warning>: u32 = 10;
        const <warning descr="Constant `___FOoBarBAZ___boo___` should have an upper case name such as `___FOO_BAR_BAZ_BOO`">___FOoBarBAZ___boo___</warning>: u32 = 10;
        const <warning descr="Constant `___123___45a___` should have an upper case name such as `___123_45A`">___123___45a___</warning>: u32 = 10;
    """)

    fun testCamelCase() = checkByText<RustStructNamingInspection>("""
        struct CamelCase {}
        struct __CamelCase__ {}
        struct _12345 {}
        struct <warning descr="Type `___` should have a camel case name such as `CamelCase`">___</warning> {}
        struct <warning descr="Type `___FOo___barBaz__` should have a camel case name such as `FooBarBaz`">___FOo___barBaz__</warning> {}
        struct <warning descr="Type `_a123456` should have a camel case name such as `A123456`">_a123456</warning> {}
    """)
}
