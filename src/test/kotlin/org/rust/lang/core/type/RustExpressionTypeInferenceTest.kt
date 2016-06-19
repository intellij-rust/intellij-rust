package org.rust.lang.core.type

import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.resolvedType
import org.assertj.core.api.Assertions.*

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

        fn main() {
            let x: S = unimplemented!();
            x;
          //^
        }
    """)

    fun testLetInitExpr() = testExpr("S",
        //language=RUST
        """
        struct S;

        fn main() {
            let x = S;
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

