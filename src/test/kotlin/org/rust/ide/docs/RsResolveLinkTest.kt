/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiManager
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.lang.core.psi.ext.RsNamedElement

class RsResolveLinkTest : RsTestBase() {

    fun `test struct`() = doTest("""
        struct Foo;
              //X
        fn foo(s: Foo) {}
           //^
    """, "Foo")

    fun `test generic struct`() = doTest("""
        struct Foo<T>(T);
        struct Bar;
             //X
        fn foo_bar() -> Foo<Bar> { unimplemented!() }
           //^
    """, "Bar")

    fun `test full path`() = doTest("""
        mod foo {
            pub struct Foo;
                      //X
        }

        fn foo(f: foo::Foo) {}
           //^
    """, "foo::Foo")

    fun `test type bound`() = doTest("""
        trait Foo {}
             //X

        fn foo<T: Foo>(t: T) {}
          //^
    """, "Foo")

    fun `test assoc type`() = doTest("""
        trait Foo {
            type Bar;
                //X
        }

        fn foo<T>(t: T) where T: Foo, T::Bar: Into<String> {}
          //^
    """, "T::Bar")

    fun `test assoc type with type qual`() = doTest("""
        trait Foo1 {
            type Bar;
        }

        trait Foo2 {
            type Bar;
        }

        struct S;

        impl Foo1 for S {
            type Bar = ();
        }      //X

        impl Foo2 for S {
            type Bar = <Self as Foo1>::Bar;
                //^
        }
    """, "<Self as Foo1>::Bar")

    fun `test struct fqn link 1`() = doTest("""
        struct Foo;
              //X
        struct Bar;
              //^
    """, "test_package/struct.Foo.html")

    fun `test struct fqn link 2`() = doTest("""
        struct Foo {}
              //X
        struct Bar;
              //^
    """, "test_package/struct.Foo.html")

    fun `test union fqn link`() = doTest("""
        union Foo { x: i32, y: f32 }
              //X
        struct Bar;
              //^
    """, "test_package/union.Foo.html")

    fun `test enum fqn link`() = doTest("""
        enum Foo { V }
            //X
        struct Bar;
              //^
    """, "test_package/enum.Foo.html")

    fun `test function fqn link`() = doTest("""
        fn foo() { }
          //X
        struct Bar;
              //^
    """, "test_package/fn.foo.html")

    fun `test bang proc macro fqn link`() = doTest("""
        #[proc_macro]
        pub fn foo(_item: TokenStream) -> TokenStream {}
             //X
        struct Bar;
              //^
    """, "test_package/macro.foo.html")

    fun `test derive proc macro fqn link`() = doTest("""
        #[proc_macro_derive(Derive)]
        pub fn foo(_item: TokenStream) -> TokenStream {}
             //X
        struct Bar;
              //^
    """, "test_package/derive.Derive.html")

    fun `test attribute proc macro fqn link`() = doTest("""
        #[proc_macro_attribute]
        pub fn foo(_attr: TokenStream, _item: TokenStream) -> TokenStream {}
             //X
        struct Bar;
              //^
    """, "test_package/attr.foo.html")

    fun `test const fqn link`() = doTest("""
        const FOO: i32 = 0;
            //X
        struct Bar;
              //^
    """, "test_package/constant.FOO.html")

    fun `test type alias fqn link`() = doTest("""
        type Foo = i32;
            //X
        struct Bar;
              //^
    """, "test_package/type.Foo.html")

    fun `test trait fqn link`() = doTest("""
        trait Foo {}
             //X
        struct Bar;
              //^
    """, "test_package/trait.Foo.html")

    fun `test trait alias fqn link`() = doTest("""
        pub trait Foo {}
        pub trait Bar {}
        pub trait FooBar = Foo + Bar;
                  //X
        struct Qwe;
              //^
    """, "test_package/traitalias.FooBar.html")

    fun `test mod fqn link`() = doTest("""
        mod foo {
            //X
        }

        struct Bar;
              //^
    """, "test_package/foo/index.html")

    fun `test macro fqn link 1`() = doTest("""
        #[macro_export]
        macro_rules! foo {
                    //X
            () => {};
        }

        struct Bar;
              //^
    """, "test_package/macro.foo.html")

    fun `test macro fqn link 2`() = doTest("""
        mod foo {
            #[macro_export]
            macro_rules! bar {
                       //X
                () => {};
            }
        }
        struct Foo;
             //^
    """, "test_package/macro.bar.html")

    fun `test macro 2 fqn link 1`() = doTest("""
        pub macro foo() {}
                 //X
        struct Bar;
              //^
    """, "test_package/macro.foo.html")

    fun `test macro 2 fqn link 2`() = doTest("""
        pub mod foo {
            pub macro bar() {}
                     //X
        }
        struct Foo;
             //^
    """, "test_package/foo/macro.bar.html")

    fun `test method fqn link`() = doTest("""
        struct Foo;
        impl Foo {
            fn foo(&self) {}
              //X
        }

        struct Bar;
              //^
    """, "test_package/struct.Foo.html#method.foo")

    fun `test tymethod fqn link`() = doTest("""
        trait Foo {
            fn foo(&self);
               //X
        }

        struct Bar;
              //^
    """, "test_package/trait.Foo.html#tymethod.foo")

    fun `test enum variant fqn link`() = doTest("""
        enum Foo {
            Var1,
            //X
            Var2
        }

        struct Bar;
              //^
    """, "test_package/enum.Foo.html#variant.Var1")

    fun `test struct field fqn link`() = doTest("""
        struct Foo {
            foo: i32
            //X
        }

        struct Bar;
              //^
    """, "test_package/struct.Foo.html#structfield.foo")

    fun `test enum variant field fqn link`() = doTest("""
        pub enum Foo {
            Bar {
                baz: i32
            }  //X
        }

        struct S;
             //^
    """, "test_package/enum.Foo.html#variant.Bar.field.baz")

    fun `test assoc type fqn link 1`() = doTest("""
        trait Foo {
            type Bar;
                //X
        }

        struct Bar;
              //^
    """, "test_package/trait.Foo.html#associatedtype.Bar")

    fun `test assoc type fqn link 2`() = doTest("""
        trait Foo {
            type Bar;
        }

        struct Bar;
              //^
        impl Foo for Bar {
            type Bar = i32;
                //X
        }
    """, "test_package/struct.Bar.html#associatedtype.Bar")

    fun `test assoc const fqn link 2`() = doTest("""
        struct Foo;
        impl Foo {
            const FOO: i32 = 123;
                 //X
        }

        struct Bar;
              //^
    """, "test_package/struct.Foo.html#associatedconstant.FOO")

    fun `test complex fqn link`() = doTest("""
        mod foo {
            mod baz {
                trait foo {
                    type foo;
                }
            }
            mod bar {
                struct foo;
                      //X
                impl foo {
                    const foo: i32 = 123;
                }
            }
            fn foo() {}
        }

        enum Baz {
            foo
        }

        struct Bar;
              //^
    """, "test_package/foo/bar/struct.foo.html")

    fun `test fqn link with direct reexports`() = doTest("""
        mod foo {
            pub mod bar {
                pub struct Baz;
                          //X
            }
            pub use crate::foo::bar::Baz;
        }

        struct Foo;
              //^
    """, "test_package/foo/struct.Baz.html")

    fun `test fqn link with module reexports`() = doTest("""
        mod foo {
            pub mod bar {
                pub mod baz {
                    pub struct Baz;
                              //X
                }
            }
            pub use crate::foo::bar::baz;
        }

        struct Foo;
              //^
    """, "test_package/foo/baz/struct.Baz.html")

    fun `test fqn link with wildcard reexport`() = doTest("""
        mod foo {
            pub mod bar {
                pub mod baz {
                    pub struct Baz;
                              //X
                }
            }
            pub use crate::foo::bar::*;
        }

        struct Foo;
              //^
    """, "test_package/foo/baz/struct.Baz.html")

    fun `test struct vs function fqn link 1`() = doTest("""
        struct foo {}
              //X
        fn foo() {}
        struct Bar;
              //^
    """, "test_package/struct.foo.html")

    fun `test struct vs function fqn link 2`() = doTest("""
        struct foo {}
        fn foo() {}
          //X
        struct Bar;
              //^
    """, "test_package/fn.foo.html")

    private fun doTest(@Language("Rust") code: String, link: String) {
        InlineFile(code, "lib.rs")
        val context = findElementInEditor<RsNamedElement>("^")
        val expectedElement = findElementInEditor<RsNamedElement>("X")
        val actualElement = RsDocumentationProvider()
            .getDocumentationElementForLink(PsiManager.getInstance(project), link, context)
        assertEquals(expectedElement, actualElement)
    }
}
