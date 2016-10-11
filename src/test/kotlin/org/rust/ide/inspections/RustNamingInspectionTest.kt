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
            type <warning descr="Type `assoc_foo` should have a camel case name such as `AssocFoo`">assoc_foo</warning>;
        }
    """)

    fun testConstants() = checkByText<RustConstNamingInspection>("""
        const CONST_OK: u32 = 12;
        const <warning descr="Constant `const_foo` should have an upper case name such as `CONST_FOO`">const_foo</warning>: u32 = 12;
    """)

    fun testEnums() = checkByText<RustEnumNamingInspection>("""
        enum EnumOk {}
        enum <warning descr="Type `enum_foo` should have a camel case name such as `EnumFoo`">enum_foo</warning> {}
    """)

    fun testEnumVariants() = checkByText<RustEnumVariantNamingInspection>("""
        enum EnumVars {
            VariantOk,
            <warning descr="Enum variant `variant_foo` should have a camel case name such as `VariantFoo`">variant_foo</warning>,
        }
    """)

    fun testEnumVariantFields() = checkByText<RustFieldNamingInspection>("""
        enum EnumVarFields {
            Variant {
                field_ok: u32,
                <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">FieldFoo</warning>: u32
            }
        }
    """)

    fun testFunctions() = checkByText<RustFunctionNamingInspection>("""
        fn fn_ok() {}
        fn <warning descr="Function `FN_BAR` should have a snake case name such as `fn_bar`">FN_BAR</warning>() {}
    """)

    fun testFunctionArguments() = checkByText<RustArgumentNamingInspection>("""
        fn fn_par(
            par_ok: u32,
            <warning descr="Argument `ParFoo` should have a snake case name such as `par_foo`">ParFoo</warning>: u32) {
        }
    """)

    fun testLifetimes() = checkByText<RustLifetimeNamingInspection>("""
        fn lifetimes<
            'lifetime_ok,
            '__,
            <warning descr="Lifetime `'LifetimeFoo` should have a snake case name such as `'lifetime_foo`">'LifetimeFoo</warning>>() {
        }
    """)

    fun testMacros() = checkByText<RustMacroNamingInspection>("""
        macro_rules! macro_ok { ( $( ${'$'}x:expr ),* ) => {}; }
        macro_rules! <warning descr="Macro `MacroFoo` should have a snake case name such as `macro_foo`">MacroFoo</warning> { ( $( ${'$'}x:expr ),* ) => {}; }
    """)

    fun testMethods() = checkByText<RustMethodNamingInspection>("""
        struct Foo {}
        impl Foo {
            fn met_ok(&self) {}
            fn <warning descr="Method `MET_BAR` should have a snake case name such as `met_bar`">MET_BAR</warning>(&self) {}
        }
    """)

    fun testMethodArguments() = checkByText<RustArgumentNamingInspection>("""
        struct Foo {}
        impl Foo {
            fn fn_par(
                par_ok: u32,
                <warning descr="Argument `ParFoo` should have a snake case name such as `par_foo`">ParFoo</warning>: u32,) {
            }
        }
    """)

    fun testTraitlMethods() = checkByText<RustMethodNamingInspection>("""
        trait Foo {
            fn met_ok() {}
            fn <warning descr="Method `MET_BAR` should have a snake case name such as `met_bar`">MET_BAR</warning>() {}
        }
    """)

    fun testModules() = checkByText<RustModuleNamingInspection>("""
        mod module_ok {}
        mod <warning descr="Module `moduleA` should have a snake case name such as `module_a`">moduleA</warning> {}
    """)

    fun testStatics() = checkByText<RustStaticConstNamingInspection>("""
        static STATIC_OK: u32 = 12;
        static <warning descr="Static constant `static_foo` should have an upper case name such as `STATIC_FOO`">static_foo</warning>: u32 = 12;
    """)

    fun testStructs() = checkByText<RustStructNamingInspection>("""
        struct StructOk {}
        struct <warning descr="Type `struct_foo` should have a camel case name such as `StructFoo`">struct_foo</warning> {}
    """)

    fun testStructFields() = checkByText<RustFieldNamingInspection>("""
        struct Foo {
            field_ok: u32,
            <warning descr="Field `FieldFoo` should have a snake case name such as `field_foo`">FieldFoo</warning>: u32
        }
    """)

    fun testTraits() = checkByText<RustTraitNamingInspection>("""
        trait TraitOk {}
        trait <warning descr="Trait `trait_foo` should have a camel case name such as `TraitFoo`">trait_foo</warning> {}
    """)

    fun testTypeAliases() = checkByText<RustTypeAliasNamingInspection>("""
        type TypeOk = u32;
        type <warning descr="Type `type_foo` should have a camel case name such as `TypeFoo`">type_foo</warning> = u32;
    """)

    fun testTypeParameters() = checkByText<RustTypeParameterNamingInspection>("""
        fn type_params<
            SomeType: Clone,
            <warning descr="Type parameter `some_Type` should have a camel case name such as `SomeType`">some_Type</warning>: Clone> () {
        }
    """)

    fun testTypeParametersWithWhere() = checkByText<RustTypeParameterNamingInspection>("""
        fn type_params<
            SomeType,
            <warning descr="Type parameter `some_Type` should have a camel case name such as `SomeType`">some_Type</warning>>() where some_Type: Clone {
        }
    """)

    fun testVariables() = checkByText<RustVariableNamingInspection>("""
        fn loc_var() {
            let var_ok = 12;
            let <warning descr="Variable `VarFoo` should have a snake case name such as `var_foo`">VarFoo</warning> = 12;
        }
    """)
}
