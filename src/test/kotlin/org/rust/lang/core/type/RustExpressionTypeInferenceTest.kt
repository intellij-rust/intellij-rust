package org.rust.lang.core.type

import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.resolvedType

class RustExpressionTypeInferenceTest : RustTypificationTestBase() {
    //language=RUST
    fun testIfLetPattern() = testExpr("""
        fn main() {
            let _ = if let Some(x) = Some(92i32) { x } else { x };
                                                 //^ <unknown>
        }
    """)

    //language=RUST
    fun testLetTypeAscription() = testExpr("""
        struct S;
        struct T;

        fn main() {
            let (x, _): (S, T) = unimplemented!();
            x;
          //^ S
        }
    """)

    //language=RUST
    fun testLetInitExpr() = testExpr("""
        struct S;
        struct T;

        fn main() {
            let (_, x) = (S, T);
            x;
          //^ T
        }
    """)

    //language=RUST
    fun testNestedStructPattern() = testExpr("""
        struct S;
        struct T {
            s: S
        }

        fn main() {
            let T { s: x } = T { s: S };
            x;
          //^ S
        }
    """)

    //language=RUST
    fun testFnArgumentPattern() = testExpr("""
        struct S;
        struct T;

        fn main((x, _): (S, T)) {
            x;
          //^ S
        }
    """)

    //language=RUST
    fun testClosureArgument() = testExpr( """
        fn main() {
            let _ = |x: ()| {
                x
              //^ ()
            };
        }
    """)

    //language=RUST
    fun testFunctionCall() = testExpr("""
        struct S;

        fn new() -> S { S }

        fn main() {
            let x = new();
            x;
          //^ S
        }
    """)

    //language=RUST
    fun testUnitFunctionCall() = testExpr("""
        fn foo() {}
        fn main() {
            let x = foo();
            x;
          //^ ()
        }
    """)

    //language=RUST
    fun testStaticMethodCall() = testExpr("""
        struct S;
        struct T;
        impl S { fn new() -> T { T } }

        fn main() {
            let x = S::new();
            x;
          //^ T
        }
    """)

    private fun testExpr(code: String) {
        val (elementAtCaret, expectedType) = configureAndFindElement(code)
        val typeAtCaret = requireNotNull(elementAtCaret.parentOfType<RustExprElement>()) {
            "No expr at caret:\n$code"
        }

        assertThat(typeAtCaret.resolvedType.toString())
            .isEqualTo(expectedType)
    }
}

