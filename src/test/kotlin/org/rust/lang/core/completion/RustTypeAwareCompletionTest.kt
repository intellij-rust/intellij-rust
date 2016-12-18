package org.rust.lang.core.completion

class RustTypeAwareCompletionTest : RustCompletionTestBase() {
    override val dataPath = ""

    fun testMethodCallExpr() = checkSingleCompletion("S.transmogrify()", """
        struct S;

        impl S { fn transmogrify(&self) {} }

        fn main() {
            S.trans/*caret*/
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
}
