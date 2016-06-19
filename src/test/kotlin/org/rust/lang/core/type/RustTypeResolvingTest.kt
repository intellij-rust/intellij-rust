package org.rust.lang.core.type

import com.intellij.psi.util.PsiTreeUtil
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.core.psi.RustTypeElement
import org.rust.lang.core.types.util.resolvedType

class RustTypeResolvingTest: RustTypificationTestBase() {
    fun testPath() = testType("Spam",
        //language=RUST
        """
        struct Spam;

        fn main() {
            let _: Spam = Spam;
                 //^
        }
    """)

    fun testQualifiedPath() = testType("<unknown>",
        //language=RUST
        """
        trait T {
            type Assoc;
        }

        struct S;

        impl T for S {
            type Assoc = S;
        }

        fn main() {
            let _: <S as T>::Assoc = S;
                 //^
        }
    """)

    fun testEnum() = testType("<unknown>",
        //language=RUST
        """
        enum E { X }

        fn main() {
            let _: E = E::X;
                 //^
        }
    """)

    /**
     * Checks the type of the element in [code] pointed to by `//^` marker.
     */
    private fun testType(expectedType: String, code: String) {
        val elementAtCaret = configureAndFindElement(code)
        val typeAtCaret = requireNotNull(
            PsiTreeUtil.getTopmostParentOfType(elementAtCaret, RustTypeElement::class.java)
        ) { "No type at caret:\n$code" }

        assertThat(typeAtCaret.resolvedType.toString())
            .isEqualTo(expectedType)
    }
}

