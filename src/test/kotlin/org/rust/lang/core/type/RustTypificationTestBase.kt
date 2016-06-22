package org.rust.lang.core.type

import com.intellij.openapi.editor.LogicalPosition
import com.intellij.psi.PsiElement
import org.rust.lang.RustTestCaseBase

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

}

