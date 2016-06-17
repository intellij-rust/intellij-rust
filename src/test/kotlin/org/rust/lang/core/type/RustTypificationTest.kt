package org.rust.lang.core.type

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustTypeElement
import org.rust.lang.core.types.util.resolvedType

class RustTypificationTest: RustTestCaseBase() {
    override val dataPath: String get() = ""

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

    private fun configureAndFindElement(code: String): PsiElement {
        val caretMarker = "//^"
        val markerOffset = code.indexOf(caretMarker)
        check(markerOffset != -1)
        myFixture.configureByText("main.rs", code)
        val markerPosition = myFixture.editor.offsetToLogicalPosition(markerOffset + caretMarker.length - 1)
        val previousLine = LogicalPosition(markerPosition.line - 1, markerPosition.column)
        val elementOffset = myFixture.editor.logicalPositionToOffset(previousLine)
        return myFixture.file.findElementAt(elementOffset)!!
    }
}

