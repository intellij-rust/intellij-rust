/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.cargo.project.workspace.PackageOrigin.STDLIB
import org.rust.ide.utils.PsiModificationUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.openapiext.moveCaretToOffset
import kotlin.math.max
import kotlin.math.min

/**
 * Removes the `dbg!` macro
 *
 * ```
 * let a = dbg!(variable);
 * ```
 *
 * to this:
 *
 * ```
 * let a = variable;
 * ```
 */
class RemoveDbgIntention : RsElementBaseIntentionAction<RsMacroCall>() {

    override fun getText() = RsBundle.message("intention.name.remove.dbg")
    override fun getFamilyName() = text

    override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): RsMacroCall? {
        val macroCall = element.ancestorStrict<RsMacroCall>() ?: return null
        if (macroCall.parent !is RsExpr) return null
        if (macroCall.macroName != "dbg") return null
        val resolvedMacro = macroCall.path.reference?.resolve()
        if (resolvedMacro != null && resolvedMacro.containingCrate.origin != STDLIB) return null
        if (!PsiModificationUtil.canReplace(macroCall)) return null

        return macroCall
    }

    override fun invoke(project: Project, editor: Editor, ctx: RsMacroCall) {
        val expr = ctx.exprMacroArgument?.expr ?: return
        var cursorOffsetToExpr = max(0, editor.caretModel.offset - expr.startOffset)
        val parent = ctx.parent.parent
        val newExpr = if (expr is RsBinaryExpr && (parent is RsBinaryExpr || parent is RsDotExpr)) {
            cursorOffsetToExpr += 1
            ctx.replaceWithExpr(RsPsiFactory(project).createExpression("(${expr.text})"))
        } else {
            ctx.replaceWithExpr(expr)
        }
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
        editor.moveCaretToOffset(newExpr, min(newExpr.startOffset + cursorOffsetToExpr, newExpr.endOffset))
    }
}
