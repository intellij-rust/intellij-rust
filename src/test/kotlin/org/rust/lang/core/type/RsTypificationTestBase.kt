/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import com.intellij.openapi.vfs.VirtualFileFilter
import org.intellij.lang.annotations.Language
import org.rust.fileTreeFromText
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.types.inference
import org.rust.lang.core.types.type
import org.rust.lang.utils.Severity
import org.rust.openapiext.Testmark

abstract class RsTypificationTestBase : RsTestBase() {
    protected fun testExpr(@Language("Rust") code: String, description: String = "", allowErrors: Boolean = false) {
        InlineFile(code)
        check(description)
        if (!allowErrors) checkNoInferenceErrors()
    }

    protected fun testExpr(
        @Language("Rust") code: String,
        mark: Testmark,
        description: String = "",
        allowErrors: Boolean = false
    ) = mark.checkHit { testExpr(code, description, allowErrors) }

    protected fun stubOnlyTypeInfer(@Language("Rust") code: String, description: String = "") {
        val testProject = fileTreeFromText(code)
            .createAndOpenFileWithCaretMarker()

        checkAstNotLoaded(VirtualFileFilter { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        })

        check(description)
    }

    private fun check(description: String) {
        val (expr, expectedType) = findElementAndDataInEditor<RsExpr>()

        check(expr.type.toString() == expectedType) {
            "Type mismatch. Expected: $expectedType, found: ${expr.type}. $description"
        }
    }

    private fun checkNoInferenceErrors() {
        val errors = myFixture.file.descendantsOfType<RsFunction>().asSequence()
            .flatMap { it.inference.diagnostics.asSequence() }
            .map { it.element to it.prepare() }
            .filter { it.second.severity == Severity.ERROR }
            .toList()
        if (errors.isNotEmpty()) {
            error(
                errors.joinToString("\n", "Detected errors during type inference: \n") {
                    "\tAt `${it.first.text}` ${it.second.errorCode.code} ${it.second.header} | ${it.second.description}"
                }
            )
        }
    }
}
