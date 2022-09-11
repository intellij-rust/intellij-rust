/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.inspections.fixes.AddRemainingArmsFix
import org.rust.ide.utils.checkMatch.Pattern
import org.rust.ide.utils.checkMatch.checkExhaustive
import org.rust.lang.core.psi.RsMatchBody
import org.rust.lang.core.psi.RsMatchExpr

open class AddRemainingArmsIntention : RsElementBaseIntentionAction<AddRemainingArmsIntention.Context>() {

    override fun getText(): String = RsBundle.message("inspection.AddRemainingArms.Fix.name")
    override fun getFamilyName(): String = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = when (val parent = element.context) {
            is RsMatchExpr -> parent
            is RsMatchBody -> parent.context as? RsMatchExpr
            else -> null
        } ?: return null

        val textRange = matchExpr.match.textRange
        val caretOffset = editor.caretModel.offset
        // `RsNonExhaustiveMatchInspection` register its quick fixes only for `match` keyword.
        // At the same time, platform shows these quick fixes even when caret is located just after the keyword,
        // i.e. `match/*caret*/`.
        // So disable this intention for such range not to provide two the same actions to a user
        if (caretOffset >= textRange.startOffset && caretOffset <= textRange.endOffset) return null

        val patterns = matchExpr.checkExhaustive() ?: return null
        return Context(matchExpr, patterns)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        val (matchExpr, patterns) = ctx
        createQuickFix(matchExpr, patterns).invoke(project, matchExpr.containingFile, matchExpr, matchExpr)
    }

    protected open fun createQuickFix(matchExpr: RsMatchExpr, patterns: List<Pattern>): AddRemainingArmsFix {
        return AddRemainingArmsFix(matchExpr, patterns)
    }

    data class Context(val matchExpr: RsMatchExpr, val patterns: List<Pattern>)
}
