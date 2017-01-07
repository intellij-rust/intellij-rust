package org.rust.ide.inspections

import com.intellij.testFramework.LightProjectDescriptor

/**
 * Tests for Deprecated API Usage inspection.
 */
class RustDeprecatedInspectionTest : RustInspectionsTestBase() {
    override val dataPath = ""

    override fun getProjectDescriptor(): LightProjectDescriptor = WithStdlibRustProjectDescriptor()

    fun testAssocFunc() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            pub struct Bar;
            impl Bar {
                #[deprecated]
                pub fn baz() {}
            }
        }
        use self::foo::Bar;
        fn main() {
            <warning descr="Method `baz` is deprecated">Bar::baz</warning>();
        }
    """)

    fun testConst() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub const BAR: u32 = 12;
        }
        fn main() {
            println!("{}", <warning descr="Constant `BAR` is deprecated">foo::BAR</warning>);
        }
    """)

    fun testEnum() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub enum Foo {
                Bar
            }
        }
        fn main() {
            let _ = <warning descr="Enum `Foo` is deprecated">foo::Foo::Bar</warning>;
        }
    """)

    fun testEnumVariant() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            pub enum Foo {
                #[deprecated]
                Bar
            }
        }
        fn main() {
            let _ = <warning descr="Enum variant `Bar` is deprecated">foo::Foo::Bar</warning>;
        }
    """)

    fun testFunction() = checkByText<RustDeprecatedInspection>("""
        #[deprecated]
        fn foo() {}
        fn main() {
            <warning descr="Function `foo` is deprecated">foo</warning>();
        }
    """)

    fun testMethod() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            pub struct Bar;
            impl Bar {
                #[deprecated]
                pub fn baz(&self) {}
            }
        }
        use self::foo::Bar;
        fn main() {
            let bar = Bar {};
            bar.<warning descr="Method `baz` is deprecated">baz</warning>();
        }
    """)

    fun testModuleOuter() = checkByText<RustDeprecatedInspection>("""
        #[deprecated]
        mod foo {
            pub const BAR: u32 = 12;
        }
        fn main() {
            let baz = <warning descr="Module `foo` is deprecated">foo::BAR</warning>;
        }
    """)

    fun testModuleInner() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #![deprecated]
            pub const BAR: u32 = 12;
        }
        fn main() {
            let baz = <warning descr="Module `foo` is deprecated">foo::BAR</warning>;
        }
    """)

    fun testModuleNested() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub mod bar {
                pub mod baz {
                    pub const BOO: u32 = 12;
                }
            }
        }
        fn main() {
            let baz = <warning descr="Module `bar` is deprecated">foo::bar::baz::BOO</warning>;
        }
    """)

    fun testStatic() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub static BAR: u32 = 12;
        }
        fn main() {
            let a = <warning descr="Static constant `BAR` is deprecated">self::foo::BAR</warning> + 10;
        }
    """)

    fun testStruct() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub struct Bar;
        }
        use self::foo::*;
        fn main() {
            let _ = <warning descr="Type `Bar` is deprecated">Bar</warning> {};
        }
    """)

    fun testStructField() = checkByText<RustDeprecatedInspection>("""
        struct Foo {
            #[deprecated]
            bar: u32,
            baz: u32
        }
        fn main() {
            let _ = Foo {
                <warning descr="Field `bar` is deprecated">bar</warning>: 12,
                baz: 37
            };
        }
    """)

    fun testTrait() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub trait Foo {}
        }
        struct Bar {}
        impl <warning descr="Trait `Foo` is deprecated">foo::Foo</warning> for Bar {}
    """)

    fun testType() = checkByText<RustDeprecatedInspection>("""
        #[deprecated]
        type Foo = u32;
        fn main() {
            let _: <warning descr="Type `Foo` is deprecated">Foo</warning>;
        }
    """)

    fun testTypeDecl() = checkByText<RustDeprecatedInspection>("""
        #[deprecated]
        struct Foo {}
        type Bar =  <warning descr="Type `Foo` is deprecated">Foo</warning>;
    """)

    fun testUseSingle() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub const BAR: u32 = 18;
        }
        use <warning descr="Constant `BAR` is deprecated">self::foo::BAR</warning>;
    """)

    fun testUseGlobList() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub const BAR: u32 = 18;
            pub const BAZ: u32 = 18;
        }
        use self::foo::{ <warning descr="Constant `BAR` is deprecated">BAR</warning>, BAZ };
    """)

    fun testUseGlobListWithModuleDeprecation() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub mod bar {
                pub mod baz {
                    pub const BOO: u32 = 18;
                }
            }
        }
        use self::foo::bar::baz::{ <warning descr="Module `bar` is deprecated">BOO</warning> };
    """)

    fun testUseMulti() = checkByText<RustDeprecatedInspection>("""
        mod foo {
            #[deprecated]
            pub mod bar {
                pub mod baz {}
            }
        }
        use <warning descr="Module `bar` is deprecated">foo::bar::baz</warning>::*;
    """)

    fun testStdLibrary() = checkByText<RustDeprecatedInspection>("""
        use <warning descr="Function `sleep_ms` is deprecated since 1.6.0: replaced by `std::thread::sleep`">std::thread::sleep_ms</warning>;
    """)

    fun testRustcDeprecation() = checkByText<RustDeprecatedInspection>("""
        #[rustc_deprecated]
        struct Foo {}
        fn main() {
            let _ = <warning descr="Type `Foo` is deprecated">Foo</warning> {};
        }
    """)

    fun testSince() = checkByText<RustDeprecatedInspection>("""
        #[deprecated(since = "3.2")]
        struct Foo {}
        fn main() {
            let _ = <warning descr="Type `Foo` is deprecated since 3.2">Foo</warning> {};
        }
    """)

    fun testNote() = checkByText<RustDeprecatedInspection>("""
        #[deprecated(note = "use Bar instead")]
        struct Foo {}
        fn main() {
            let _ = <warning descr="Type `Foo` is deprecated: use Bar instead">Foo</warning> {};
        }
    """)

    fun testReason() = checkByText<RustDeprecatedInspection>("""
        #[rustc_deprecated(reason = "replaced by `bar`")]
        fn foo() {}
        fn main() {
            <warning descr="Function `foo` is deprecated: replaced by `bar`">foo</warning>();
        }
    """)

    fun testSinceAndNote() = checkByText<RustDeprecatedInspection>("""
        #[deprecated(note = "use `Bar` instead", since = "3.4")]
        struct Foo {}
        fn main() {
            let _ = <warning descr="Type `Foo` is deprecated since 3.4: use `Bar` instead">Foo</warning> {};
        }
    """)

    fun testSinceAndReason() = checkByText<RustDeprecatedInspection>("""
        #[rustc_deprecated(since = "0.9", reason = "replaced by `bar`")]
        fn foo() {}
        fn main() {
            <warning descr="Function `foo` is deprecated since 0.9: replaced by `bar`">foo</warning>();
        }
    """)
}
