/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsParenExpr

class RemoveRedundantParenthesesFix(element: RsParenExpr) : RsQuickFixBase<RsParenExpr>(element) {
    override fun getText(): String = "Remove parentheses from expression"

    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsParenExpr) {
        val wrapped = element.expr ?: return
        element.replace(wrapped)
    }
}
