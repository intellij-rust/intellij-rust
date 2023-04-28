/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsPatternArgumentInFunctionWithoutBodyTest : RsAnnotatorTestBase(RsSyntaxErrorsAnnotator::class) {
    fun `test E0642 compiler test -- E0642`() = checkByText("""
        struct S;

        trait T {
            fn foo(<error descr="Patterns aren't allowed in functions without bodies [E0642]">(x, y)</error>: (i32, i32));
            fn bar(<error descr="Patterns aren't allowed in functions without bodies [E0642]">(x, y)</error>: (i32, i32));
            fn method(<error descr="Patterns aren't allowed in functions without bodies [E0642]">S { .. }</error>: S);

            fn f(&ident: &S) {} // ok
            fn g(&&ident: &&S) {} // ok
            fn h(mut ident: S) {} // ok
        }
    """)

    fun `test E0642 compiler test -- issue-50571`() = checkByText("""
        trait Foo {
            fn foo(<error descr="Patterns aren't allowed in functions without bodies [E0642]">[a, b]</error>: [i32; 2]);
        }
    """)

    fun `test E0642 compiler test -- no-patterns-in-args-2`() = checkByText("""
        trait Tr {
            fn f1(<error descr="Patterns aren't allowed in functions without bodies [E0642]">mut arg</error>: u8);
            fn f2(<error descr="Patterns aren't allowed in functions without bodies [E0642]">&arg</error>: u8);
            fn g1(arg: u8);
            fn g2(_: u8);
        }
    """)

    fun `test E0642 macros`() = checkByText("""
        macro_rules! foo {
            () => { _ };
        }

        macro_rules! proxy_foo {
            () => { foo!() };
        }

        macro_rules! bad {
            () => { (a, b) };
        }

        macro_rules! proxy_bad {
            () => { bad!() };
        }

        trait A {
            fn foo(foo!(): (i32, i32)); // OK
            fn proxy_foo(proxy_foo!(): (i32, i32)); // OK
            fn bad(<error descr="Patterns aren't allowed in functions without bodies [E0642]">bad!()</error>: (i32, i32));
            fn proxy_bad(<error descr="Patterns aren't allowed in functions without bodies [E0642]">proxy_bad!()</error>: (i32, i32));
        }
    """)
}
