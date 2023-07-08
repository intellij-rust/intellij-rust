/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsUnaryExpr

class ReplaceBoxSyntaxFix(element: RsUnaryExpr): RsQuickFixBase<RsUnaryExpr>(element) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.replace.box.with.box.new")

    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsUnaryExpr) {
        if (element.box == null) return
        val exprText = element.expr?.text ?: return
        element.replace(RsPsiFactory(project).createBox(exprText))
    }
}
