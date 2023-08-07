/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsExprStmt
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict

class SurroundWithUnsafeFix(expr: RsExpr) : RsQuickFixBase<RsExpr>(expr) {
    override fun getFamilyName() = text
    override fun getText() = RsBundle.message("intention.name.surround.with.unsafe.block")

    override fun invoke(project: Project, editor: Editor?, element: RsExpr) {
        val target = element.ancestorStrict<RsExprStmt>() ?: element
        val unsafeBlockExpr = RsPsiFactory(project).createUnsafeBlockExprOrStmt(target)
        target.replace(unsafeBlockExpr)
    }
}
