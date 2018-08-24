/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import org.rust.ProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

@ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
class RsExternalDocUrlTest : RsDocumentationProviderTest() {
    fun `test not stdlib item`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;
                  //^
    """, "test://doc-host.org/dep_lib_target/struct.Foo.html")

    fun `test associated const`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        pub struct Foo;

        impl Foo {
            pub const BAR: i32 = 123;
                     //^
        }
    """, "test://doc-host.org/dep_lib_target/struct.Foo.html#associatedconstant.BAR")

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

    fun `test doc hidden`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[doc(hidden)]
        pub fn foo() {}
               //^
    """, null, RsDocumentationProvider.Testmarks.docHidden)

    fun `test macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
                    //^
            () => { unimplemented!() };
        }
    """, "test://doc-host.org/dep_lib_target/macro.foo.html")

    fun `test not exported macro`() = doUrlTestByFileTree("""
        //- dep-lib/lib.rs
        macro_rules! foo {
                    //^
            () => { unimplemented!() };
        }
    """, null, RsDocumentationProvider.Testmarks.notExportedMacro)
}
