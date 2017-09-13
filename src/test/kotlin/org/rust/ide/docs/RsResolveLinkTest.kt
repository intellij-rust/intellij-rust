/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.psi.PsiManager
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
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
               //X
        }

        trait Foo2 {
            type Bar;
        }

        struct S;

        impl Foo1 for S {
            type Bar = ();
        }

        impl Foo2 for S {
            type Bar = <Self as Foo1>::Bar;
                //^
        }
    """, "<Self as Foo1>::Bar")

    private fun doTest(@Language("Rust") code: String, link: String) {
        InlineFile(code)
        val context = findElementInEditor<RsNamedElement>("^")
        val expectedElement = findElementInEditor<RsNamedElement>("X")
        val actualElement = RsDocumentationProvider()
            .getDocumentationElementForLink(PsiManager.getInstance(project), link, context)
        check(actualElement == expectedElement)
    }
}
