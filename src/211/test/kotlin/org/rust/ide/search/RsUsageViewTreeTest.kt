/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

class RsUsageViewTreeTest : RsUsageViewTreeTestBase() {

    fun `test grouping function usages`() = doTestByText("""
        fn foo() {}
          //^

        fn bar() {
            foo();
        }

        fn baz() {
            foo();
        }
    """, """
        <root> (2)
         Function
          foo
         Found usages (2)
          function call (2)
           main.rs (2)
            6foo();
            10foo();
    """)

    fun `test grouping struct usages`() = doTestByText("""
        struct S {
             //^
            a: usize,
        }

        impl S {}
        impl S {}

        fn foo(s1: &S) {}

        fn bar() {
            let s1 = S { a: 1 };
            let a = 1;
            let s2 = S { a };
        }
    """, """
        <root> (5)
         Struct
          S
         Found usages (5)
          impl (2)
           main.rs (2)
            7impl S {}
            8impl S {}
          init struct (2)
           main.rs (2)
            13let s1 = S { a: 1 };
            15let s2 = S { a };
          type reference (1)
           main.rs (1)
            10fn foo(s1: &S) {}
    """)
}
