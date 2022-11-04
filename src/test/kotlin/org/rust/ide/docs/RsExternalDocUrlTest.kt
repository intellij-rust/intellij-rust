/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import org.intellij.lang.annotations.Language
import org.rust.CheckTestmarkHit
import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.ide.docs.RsDocumentationProvider.Testmarks

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsExternalDocUrlTest : RsDocumentationProviderTest() {
    fun `test not stdlib item`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;
                  //^
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/struct.Foo.html")

    fun `test associated const`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;

        impl Foo {
            pub const BAR: i32 = 123;
                     //^
        }
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/struct.Foo.html#associatedconstant.BAR")

    fun `test enum variant field`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub enum Foo {
            Bar {
                baz: i32
            }  //^
        }
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/enum.Foo.html#variant.Bar.field.baz")

    fun `test union`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub union Foo { x: i32, y: f32 }
                 //^
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/union.Foo.html")

    fun `test trait alias`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub trait Foo {}
        pub trait Bar {}
        pub trait FooBar = Foo + Bar;
                 //^
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/traitalias.FooBar.html")

    fun `test item with restricted visibility`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub(crate) enum Foo { V1, V2 }
                      //^
    """, null)

    fun `test private item`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        struct Foo;
              //^
    """, null)

    fun `test pub item in private module`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        mod foo {
            pub struct Foo;
                       //^
        }
    """, null)

    fun `test method in private trait`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        trait Foo {
            fn foo(&self);
              //^
        }
    """, null)

    fun `test private method`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;
        impl Foo {
            fn foo(&self) {}
              //^
        }
    """, null)

    fun `test public method in private module`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;
        mod bar {
            impl crate::Foo {
                pub fn foo(&self) {}
                      //^
            }
        }
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/struct.Foo.html#method.foo")

    @CheckTestmarkHit(Testmarks.DocHidden::class)
    fun `test doc hidden`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[doc(hidden)]
        pub fn foo() {}
               //^
    """, null)

    fun `test macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
                    //^
            () => { unimplemented!() };
        }
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/macro.foo.html")

    fun `test macro in module`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub mod bar {
            #[macro_export]
            macro_rules! foo {
                        //^
                () => { unimplemented!() };
            }
        }
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/macro.foo.html")

    @CheckTestmarkHit(Testmarks.NotExportedMacro::class)
    fun `test not exported macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        macro_rules! foo {
                    //^
            () => { unimplemented!() };
        }
    """, null)

    fun `test macro 2`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub macro bar() {}
                 //^
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/macro.bar.html")

    fun `test macro 2 in module`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub mod foo {
            pub macro bar() {}
        }            //^

    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/foo/macro.bar.html")

    fun `test bang proc macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[proc_macro]
        fn foo(_input: TokenStream) -> TokenStream {}
          //^
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/macro.foo.html")

    fun `test derive proc macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[proc_macro_derive(MyDerive)]
                           //^
        fn foo(_input: TokenStream) -> TokenStream {}
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/derive.MyDerive.html")

    fun `test attribute proc macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[proc_macro_attribute]
        fn foo(_attr: TokenStream, _input: TokenStream) -> TokenStream {}
          //^
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/attr.foo.html")

    @CheckTestmarkHit(Testmarks.NonDependency::class)
    fun `test not external url for workspace package`() = doUrlTestByFileTree("""
        //- lib.rs
        pub enum Foo { FOO, BAR }
                //^
    """, null)

    @CheckTestmarkHit(Testmarks.PkgWithoutSource::class)
    fun `test not external url for dependency package without source`() = doUrlTestByFileTree("""
        //- no-source-lib/lib.rs
        pub enum Foo { FOO, BAR }
                //^
    """, null)

    fun `test custom documentation URL`() = doCustomUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
                    //^
            () => { unimplemented!() };
        }
    """, "file:///mydoc/", "file:///mydoc/dep-lib/0.0.1/dep_lib_target/macro.foo.html")

    fun `test custom documentation URL add slash`() = doCustomUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
                    //^
            () => { unimplemented!() };
        }
    """, "file:///mydoc", "file:///mydoc/dep-lib/0.0.1/dep_lib_target/macro.foo.html")

    private fun doCustomUrlTestByFileTree(@Language("Rust") text: String, docBaseUrl: String, expectedUrl: String) {
        withExternalDocumentationBaseUrl(docBaseUrl) {
            doUrlTestByFileTree(text, expectedUrl)
        }
    }
}
