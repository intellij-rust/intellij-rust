/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsWhileExpr
import org.rust.lang.core.psi.ext.RsLabelReferenceOwner
import org.rust.lang.core.psi.ext.ancestorStrict

class RsAddLabelFix(element: RsLabelReferenceOwner): RsQuickFixBase<RsLabelReferenceOwner>(element) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.add.label")

    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsLabelReferenceOwner) {
        if (editor == null) return
        val whileExpr = element.ancestorStrict<RsWhileExpr>() ?: return
        val labelDeclaration = RsPsiFactory(project).createLabelDeclaration("a")
        whileExpr.addBefore(labelDeclaration, whileExpr.firstChild)
        element.add(RsPsiFactory(project).createLabel("a"))
    }
}
