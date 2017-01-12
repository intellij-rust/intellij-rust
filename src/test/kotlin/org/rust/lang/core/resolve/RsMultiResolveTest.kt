package org.rust.lang.core.resolve

import org.rust.lang.core.psi.RsReferenceElement


class RsMultiResolveTest : RsResolveTestBase() {
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
        InlineFile(code)
        val ref = findElementInEditor<RsReferenceElement>().reference
        check(ref.multiResolve().size == 2)
    }
}
