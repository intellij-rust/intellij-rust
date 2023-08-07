/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.lang.core.psi.RsBlockExpr
import org.rust.lang.core.psi.RsPsiFactory

class RsConvertBlockToLoopFix(element: RsBlockExpr): RsQuickFixBase<RsBlockExpr>(element) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.convert.to.loop")

    override fun getText(): String = familyName

    override fun invoke(project: Project, editor: Editor?, element: RsBlockExpr) {
        val labelName = element.labelDecl?.name ?: return
        element.replace(RsPsiFactory(project).createLoop(element.block.text, labelName))
    }
}
