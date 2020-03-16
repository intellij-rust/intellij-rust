/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.ext.RsReferenceElement


class RsMultiResolveTest : RsResolveTestBase() {
    fun `test struct expr`() = doTest("""
        struct S { foo: i32, foo: () }
        fn main() {
            let _ = S { foo: 1 };
                       //^
        }
    """)

    fun `test struct field shorthand`() = doTest("""
        struct S { foo: i32, bar: fn() }
        fn foo() {}
        fn main() {
            let bar = 62;
            let _ = S { bar, foo };
        }                   //^
    """)

    fun `test field expr`() = doTest("""
        struct S { foo: i32, foo: () }
        fn f(s: S) {
            s.foo
             //^
        }
    """)

    fun `test use multi reference`() = doTest("""
        use m::foo;
              //^

        mod m {
            fn foo() {}
            mod foo {}
        }
    """)

    fun `test other mod trait bound method`() = doTest("""
        mod foo {
            pub trait Trait {
                fn foo(&self) {}
                fn foo(&self) {}
            }
        }
        fn bar<T: foo::Trait>(t: T) {
            t.foo(a);
        }   //^
    """)

    fun `test trait object inherent impl`() = doTest("""
        trait Foo { fn foo(&self){} }
        impl dyn Foo { fn foo(&self){} }
        fn foo(a: &dyn Foo){
            a.foo()
        }   //^
    """)

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val element = findElementInEditor<RsReferenceElement>()
        val ref = element.reference ?: error("Failed to get reference for `${element.text}`")
        check(ref.multiResolve().size == 2) {
            "Expected 2 variants, got ${ref.multiResolve()}"
        }
    }
}
