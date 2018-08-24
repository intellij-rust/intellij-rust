/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import com.intellij.openapi.vfs.VirtualFileFilter
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.RsInferenceContextOwner
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
        checkAllExpressionsTypified()
    }

    protected fun testExpr(
        @Language("Rust") code: String,
        mark: Testmark,
        description: String = "",
        allowErrors: Boolean = false
    ) = mark.checkHit { testExpr(code, description, allowErrors) }

    protected fun stubOnlyTypeInfer(@Language("Rust") code: String, description: String = "", allowErrors: Boolean = false) {
        val testProject = fileTreeFromText(code)
            .createAndOpenFileWithCaretMarker()

        checkAstNotLoaded(VirtualFileFilter { file ->
            !file.path.endsWith(testProject.fileWithCaret)
        })

        check(description)
        if (!allowErrors) checkNoInferenceErrors()
        checkAllExpressionsTypified()
    }

    private fun check(description: String) {
        val (expr, expectedType) = findElementAndDataInEditor<RsExpr>()

        check(expr.type.toString() == expectedType) {
            "Type mismatch. Expected: $expectedType, found: ${expr.type}. $description"
        }
    }

    private fun checkNoInferenceErrors() {
        val errors = myFixture.file.descendantsOfType<RsInferenceContextOwner>().asSequence()
            .flatMap { it.inference.diagnostics.asSequence() }
            .map { it.element to it.prepare() }
            .filter { it.second.severity == Severity.ERROR }
            .toList()
        if (errors.isNotEmpty()) {
            error(
                errors.joinToString("\n", "Detected errors during type inference: \n") {
                    "\tAt `${it.first.text}` (line ${it.first.lineNumber}) " +
                        "${it.second.errorCode.code} ${it.second.header} | ${it.second.description}"
                }
            )
        }
    }

    private fun checkAllExpressionsTypified() {
        val notTypifiedExprs = myFixture.file.descendantsOfType<RsExpr>().filter { expr ->
            expr.inference?.isExprTypeInferred(expr) == false
        }
        if (notTypifiedExprs.isNotEmpty()) {
            error(
                notTypifiedExprs.joinToString(
                    "\n",
                    "Some expressions are not typified during type inference: \n",
                    "\nNote: All `RsExpr`s must be typified during type inference"
                ) { "\tAt `${it.text}` (line ${it.lineNumber})" }
            )
        }
    }
}
