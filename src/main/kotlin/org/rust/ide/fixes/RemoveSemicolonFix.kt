/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsExprStmt

open class RemoveSemicolonFix(
    element: RsExprStmt,
) : RsQuickFixBase<RsExprStmt>(element) {
    override fun getText(): String = "Remove semicolon"
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsExprStmt) {
        element.semicolon?.delete()
    }
}
