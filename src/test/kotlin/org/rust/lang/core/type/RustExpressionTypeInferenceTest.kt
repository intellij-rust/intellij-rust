package org.rust.lang.core.type

import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.resolvedType

class RustExpressionTypeInferenceTest : RustTypificationTestBase() {
    fun testIfLetPattern() = testExpr("<unknown>",
        //language=RUST
        """
        fn main() {
            let _ = if let Some(x) = Some(92i32) { x } else { x };
                                                 //^
        }
    """)

    fun testLetTypeAscription() = testExpr("S",
        //language=RUST
        """
        struct S;
        struct T;

        fn main() {
            let (x, _): (S, T) = unimplemented!();
            x;
          //^
        }
    """)

    fun testLetInitExpr() = testExpr("T",
        //language=RUST
        """
        struct S;
        struct T;

        fn main() {
            let (_, x) = (S, T);
            x;
          //^
        }
    """)

    fun testNestedStructPattern() = testExpr("S",
        //language=RUST
        """
        struct S;
        struct T {
            s: S
        }

        fn main() {
            let T { s: x } = T { s: S };
            x;
          //^
        }
    """)

    fun testFnArgumentPattern() = testExpr("S",
        //language=RUST
        """
        struct S;
        struct T;

        fn main((x, _): (S, T)) {
            x;
          //^
        }
    """)

    fun testClosureArgument() = testExpr("()",
        //language=RUST
        """
        fn main() {
            let _ = |x: ()| {
                x
              //^
            };
        }
    """)

    fun testFunctionCall() = testExpr("S",
        //language=RUST
        """
        struct S;

        fn new() -> S { S }

        fn main() {
            let x = new();
            x;
          //^
        }
    """)

    fun testUnitFunctionCall() = testExpr("()",
        //language=RUST
        """
        fn foo() {}
        fn main() {
            let x = foo();
            x;
          //^
        }
    """)

    private fun testExpr(expectedType: String, code: String) {
        val elementAtCaret = configureAndFindElement(code)
        val typeAtCaret = requireNotNull(elementAtCaret.parentOfType<RustExprElement>()) {
            "No expr at caret:\n$code"
        }

        assertThat(typeAtCaret.resolvedType.toString())
            .isEqualTo(expectedType)
    }
}

