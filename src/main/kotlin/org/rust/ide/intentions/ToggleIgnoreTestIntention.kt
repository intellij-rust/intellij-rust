/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.findOuterAttr
import org.rust.lang.core.psi.ext.isTest

class ToggleIgnoreTestIntention: RsElementBaseIntentionAction<ToggleIgnoreTestIntention.Context>() {

    data class Context(
        val element: RsFunction
    )

    override fun getText() = "Toggle ignore for tests"
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val function = element.parent as? RsFunction ?: return null
        if (!function.isTest) return null
        return Context(function)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val existingIgnore = ctx.element.findOuterAttr("ignore")
        if (existingIgnore == null) {
            val ignore = RsPsiFactory(project).createOuterAttr("ignore")
            val test = ctx.element.findOuterAttr("test")
            ctx.element.addBefore(ignore, test)
        } else {
            existingIgnore.delete()
        }
    }
}
