/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.actions.ShareInPlaygroundAction
import org.rust.ide.actions.ShareInPlaygroundAction.Context
import org.rust.lang.core.psi.RsFile

class ShareInPlaygroundIntention : RsElementBaseIntentionAction<Context>(), LowPriorityAction {

    override fun startInWriteAction(): Boolean = false

    override fun getText(): String = familyName
    override fun getFamilyName(): String = RsBundle.message("action.Rust.ShareInPlayground.text")

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val file = element.containingFile as? RsFile ?: return null
        val selectedText = editor.selectionModel.selectedText ?: return null
        return Context(file, selectedText, true)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        ShareInPlaygroundAction.performAction(project, ctx)
    }
}
