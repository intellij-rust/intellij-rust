package org.rust.lang.core.type

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.types.util.resolvedType

abstract class RustTypificationTestBase : RustTestCaseBase() {
    override val dataPath: String get() = ""

    protected fun testExpr(@Language("Rust") code: String) {
        val (elementAtCaret, expectedType) = configureAndFindElement(code)
        val typeAtCaret = requireNotNull(elementAtCaret.parentOfType<RustExprElement>()) {
            "No expr at caret:\n$code"
        }

        assertThat(typeAtCaret.resolvedType.toString())
            .isEqualTo(expectedType)
    }
}

