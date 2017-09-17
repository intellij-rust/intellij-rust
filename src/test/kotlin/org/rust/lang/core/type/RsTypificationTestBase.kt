/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.types.type

abstract class RsTypificationTestBase : RsTestBase() {
    protected fun testExpr(@Language("Rust") code: String, description: String = "") {
        InlineFile(code)
        val (expr, expectedType) = findElementAndDataInEditor<RsExpr>()

        check(expr.type.toString() == expectedType) {
            "Type mismatch. Expected: $expectedType, found: ${expr.type}. $description"
        }
    }
}
