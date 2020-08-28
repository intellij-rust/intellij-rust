/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.ide.docs.RsDocumentationProviderBase.Testmarks

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

    fun `test doc hidden`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[doc(hidden)]
        pub fn foo() {}
               //^
    """, null, Testmarks.docHidden)

    fun `test macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
                    //^
            () => { unimplemented!() };
        }
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/macro.foo.html")

    fun `test not exported macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        macro_rules! foo {
                    //^
            () => { unimplemented!() };
        }
    """, null, Testmarks.notExportedMacro)

    fun `test macro 2`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub macro Bar() {}
                 //^
    """, "https://docs.rs/dep-lib/0.0.1/dep_lib_target/macro.Bar.html")

    fun `test not external url for workspace package`() = doUrlTestByFileTree("""
        //- lib.rs
        pub enum Foo { FOO, BAR }
                //^
    """, null, Testmarks.nonDependency)

    fun `test not external url for dependency package without source`() = doUrlTestByFileTree("""
        //- no-source-lib/lib.rs
        pub enum Foo { FOO, BAR }
                //^
    """, null, Testmarks.pkgWithoutSource)
}
