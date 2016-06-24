package org.rust.lang.core.type

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.psi.PsiElement
import org.assertj.core.api.Assertions.assertThat
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.resolvedType

abstract class RustTypificationTestBase : RustTestCaseBase() {
    override val dataPath: String get() = ""

    protected fun configureAndFindElement(code: String): Pair<PsiElement, String> {
        val caretMarker = "//^"
        val markerOffset = code.indexOf(caretMarker)
        val data = code.drop(markerOffset).removePrefix(caretMarker).takeWhile { it != '\n' }.trim()
        check(markerOffset != -1)
        myFixture.configureByText("main.rs", code)
        val markerPosition = myFixture.editor.offsetToLogicalPosition(markerOffset + caretMarker.length - 1)
        val previousLine = LogicalPosition(markerPosition.line - 1, markerPosition.column)
        val elementOffset = myFixture.editor.logicalPositionToOffset(previousLine)
        return myFixture.file.findElementAt(elementOffset)!! to data
    }

    protected fun testExpr(code: String) {
        val (elementAtCaret, expectedType) = configureAndFindElement(code)
        val typeAtCaret = requireNotNull(elementAtCaret.parentOfType<RustExprElement>()) {
            "No expr at caret:\n$code"
        }

        assertThat(typeAtCaret.resolvedType.toString())
            .isEqualTo(expectedType)
    }
}

