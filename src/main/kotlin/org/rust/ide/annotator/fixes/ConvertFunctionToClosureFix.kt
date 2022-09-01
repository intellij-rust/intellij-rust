/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.intentions.ConvertFunctionToClosureIntention
import org.rust.lang.core.psi.RsFunction

class ConvertFunctionToClosureFix(function: RsFunction) : LocalQuickFixAndIntentionActionOnPsiElement(function) {

    override fun getText(): String = "Convert function to closure"
    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val function = startElement as? RsFunction ?: return
        ConvertFunctionToClosureIntention().doInvoke(project, editor, function)
    }
}
