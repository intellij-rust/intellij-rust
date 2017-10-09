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
import org.rust.lang.core.types.type

abstract class RsTypificationTestBase : RsTestBase() {
    protected fun testExpr(@Language("Rust") code: String, description: String = "") {
        InlineFile(code)
        check(description)
    }

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
}
