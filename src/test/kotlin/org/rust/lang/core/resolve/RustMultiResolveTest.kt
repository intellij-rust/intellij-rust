package org.rust.lang.core.resolve

import org.rust.lang.core.psi.RustReferenceElement


class RustMultiResolveTest : RustResolveTestBase() {
    fun testStructExpr() = doTest("""
        struct S { foo: i32, foo: () }
        fn main() {
            let _ = S { foo: 1 };
                       //^
        }
    """)

    fun testFieldExpr() = doTest("""
        struct S { foo: i32, foo: () }
        fn f(s: S) {
            s.foo
             //^
        }
    """)

    fun testUseMultiReference() = doTest("""
        use m::foo;
              //^

        mod m {
            fn foo() {}
            mod foo {}
        }
    """)

    private fun doTest(code: String) {
        val ref = checkNotNull(
            InlineFile(code).elementAtCaret<RustReferenceElement>().reference
        ) { "No poly reference at caret " }
        check(ref.multiResolve().size == 2)
    }
}
