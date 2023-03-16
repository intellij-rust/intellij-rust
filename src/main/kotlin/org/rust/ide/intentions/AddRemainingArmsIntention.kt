/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.ide.fixes.AddRemainingArmsFix
import org.rust.ide.utils.checkMatch.Pattern
import org.rust.ide.utils.checkMatch.checkExhaustive
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsMatchBody
import org.rust.lang.core.psi.RsMatchExpr

open class AddRemainingArmsIntention : RsElementBaseIntentionAction<AddRemainingArmsIntention.Context>() {

    override fun getText(): String = AddRemainingArmsFix.NAME
    override fun getFamilyName(): String = text

    data class Context(
        val matchExpr: RsMatchExpr,
        val expr: RsExpr,
        val patterns: List<Pattern>,
        val place: AddRemainingArmsFix.ArmsInsertionPlace,
    )

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Context? {
        val matchExpr = when (val parent = element.context) {
            is RsMatchExpr -> parent
            is RsMatchBody -> parent.context as? RsMatchExpr
            else -> null
        } ?: return null

        // `RsNonExhaustiveMatchInspection` register its quick fixes only for `match` keyword.
        // At the same time, platform shows these quick fixes even when caret is located just after the keyword,
        // i.e. `match/*caret*/`.
        // So disable this intention for such range not to provide two the same actions to a user
        if (matchExpr.match.textRange.containsOffset(editor.caretModel.offset)) return null

        val expr = matchExpr.expr ?: return null
        val patterns = matchExpr.checkExhaustive() ?: return null
        val place = AddRemainingArmsFix.findArmsInsertionPlaceIn(matchExpr) ?: return null
        return Context(matchExpr, expr, patterns, place)
    }

    override fun invoke(project: Project, editor: Editor, ctx: Context) {
        createQuickFix(ctx.matchExpr, ctx.patterns).invoke(
            project,
            ctx.matchExpr,
            ctx.expr,
            ctx.place
        )
    }

    protected open fun createQuickFix(matchExpr: RsMatchExpr, patterns: List<Pattern>): AddRemainingArmsFix {
        return AddRemainingArmsFix(matchExpr, patterns)
    }
}
