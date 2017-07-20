/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

class RsTypeAwareCompletionTest : RsCompletionTestBase() {
    fun `test method call only self`() = doSingleCompletion("""
        struct S;
        impl S { fn frobnicate(self) {} }
        fn main() { S.frob/*caret*/ }
    """, """
        struct S;
        impl S { fn frobnicate(self) {} }
        fn main() { S.frobnicate()/*caret*/ }
    """)

    fun `test method call self and arg`() = doSingleCompletion("""
        struct S;
        impl S { fn frobnicate(self, foo: i32) {} }
        fn main() { S.frob/*caret*/ }
    """, """
        struct S;
        impl S { fn frobnicate(self, foo: i32) {} }
        fn main() { S.frobnicate(/*caret*/) }
    """)

    fun `test don't suggest fields for methods`() = doSingleCompletion("""
        struct S { transmogrificator: i32 }
        impl S { fn transmogrify(&self) {} }

        fn main() {
            let x: S = unimplemented!();
            x.trans/*caret*/()
        }
    """, """
        struct S { transmogrificator: i32 }
        impl S { fn transmogrify(&self) {} }

        fn main() {
            let x: S = unimplemented!();
            x.transmogrify()/*caret*/
        }
    """)

    fun testMethodCallExprRef() = checkSingleCompletion("self.transmogrify()", """
        struct S;

        impl S {
            fn transmogrify(&self) {}

            fn foo(&self) {
                self.trans/*caret*/
            }
        }
    """)

    fun testMethodCallExprEnum() = checkSingleCompletion("self.quux()", """
        enum E { X }

        impl E {
            fn quux(&self) {}

            fn bar(&self) {
                self.qu/*caret*/
            }
        }
    """)

    fun testFieldExpr() = checkSingleCompletion("transmogrificator", """
        struct S { transmogrificator: f32 }

        fn main() {
            let s = S { transmogrificator: 92};
            s.trans/*caret*/
        }
    """)

    fun testStaticMethod() = checkSingleCompletion("S::create()", """
        struct S;

        impl S {
            fn create() -> S { S }
        }

        fn main() {
            let _ = S::cr/*caret*/;
        }
    """)

    fun testSelfMethod() = checkSingleCompletion("frobnicate()", """
        trait Foo {
            fn frobnicate(&self);
            fn bar(&self) { self.frob/*caret*/ }
        }
    """)

    fun `test default method`() = checkSingleCompletion("frobnicate()", """
        trait Frob { fn frobnicate(&self) { } }
        struct S;
        impl Frob for S {}

        fn foo(s: S) { s.frob/*caret*/ }
    """)
}
