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

    //FIXME: should resolve to a single  non ref method!
    fun `test non inherent impl 2`() = doTest("""
        trait T { fn foo(&self) { println!("Hello"); } }

        struct S;

        impl T for S { fn foo(&self) { println!("non ref"); } }

        impl<'a> T for &'a S { fn foo(&self) { println!("ref"); } }
                                 //X

        fn main() {
            let x: &S = &S;
            x.foo()
              //^
        }
    """)

    private fun doTest(@Language("Rust") code: String) {
        InlineFile(code)
        val ref = findElementInEditor<RsReferenceElement>().reference
        check(ref.multiResolve().size == 2) {
            "Expected 2 variants, got ${ref.multiResolve()}"
        }
    }
}
