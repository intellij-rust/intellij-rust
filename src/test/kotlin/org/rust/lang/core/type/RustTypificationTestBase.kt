package org.rust.lang.core.type

import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.types.util.resolvedType

abstract class RustTypificationTestBase : RustTestCaseBase() {
    override val dataPath: String get() = ""

    protected fun testExpr(@Language("Rust") code: String) {
        val (expr, expectedType) = InlineFile(code).elementAndData<RustExprElement>()

        assertThat(expr.resolvedType.toString())
            .isEqualTo(expectedType)
    }
}

