/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.infer.mutabilityCategory
import org.rust.openapiext.Testmark

abstract class MemoryCategorizationTestBase : RsTestBase() {
    protected fun testExpr(@Language("Rust") code: String, description: String = "", allowErrors: Boolean = false) {
        InlineFile(code)
        check(description)
    }

    protected fun testExpr(
        @Language("Rust") code: String,
        mark: Testmark,
        description: String = "",
        allowErrors: Boolean = false
    ) = mark.checkHit { testExpr(code, description, allowErrors) }

    private fun check(description: String) {
        val (expr, expectedCategory) = findElementAndDataInEditor<RsExpr>()

        val category = expr.mutabilityCategory
        check(category.toString() == expectedCategory) {
            "Category mismatch. Expected: $expectedCategory, found: $category. $description"
        }
    }

}
